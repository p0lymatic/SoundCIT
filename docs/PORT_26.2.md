# Разведка: что меняется при переходе 1.21.1 → 26.2

Проверено **по деобфусцированному клиенту 26.2** (Mojang с некоторых пор отдаёт jar с настоящими
именами — сигнатуры читаются напрямую через `javap`, без декомпиляции) и по исходникам
NeoForge `26.2.0.35-beta`. Ничего не взято по памяти.

## Итог одной строкой

Архитектура выживает: главный несущий элемент — приватный funnel в `ClientLevel` — на месте, все
события NeoForge целы. Сломана ровно одна содержательная вещь: **опознание трезубца в полёте**.
Остальное — механические правки сигнатур и переезды классов.

---

## Окружение

| Что | 1.21.1 | 26.2 |
|---|---|---|
| Java | 21 | **25** |
| Gradle | 8.x | 9.1+ |
| ModDevGradle | 2.0.142 | 2.0.141+ |
| Parchment | нужен | **не нужен**, Mojang отдаёт имена параметров |
| NeoForge | 21.1.242 (стабильный) | **26.2.0.35-beta** — стабильных для 26.2 пока нет |
| Формат ресурспака | `RESOURCE_PACK_FORMAT = 34` | `RESOURCE_PACK_FORMAT_MAJOR = 88`, `MINOR = 0` |

Отдельно: под 26.1 стабильные сборки NeoForge есть (`26.1.2.87`), под 26.2 — только беты. Если
важна стабильность, промежуточной целью может быть 26.1.

---

## Уцелело без изменений

| Что | Почему это важно |
|---|---|
| `ClientLevel.playSound(double,double,double,SoundEvent,SoundSource,float,float,boolean,long)` — приватный funnel | **Несущий элемент всей архитектуры.** Через него проходят все позиционные звуки, включая `playLocalSound` и звуки уровня |
| `ClientLevel.playLocalSound(Entity, SoundEvent, SoundSource, float, float)` | Захват entity-bound звуков |
| `ServerLevel.broadcastEntityEvent(Entity, byte)` | Подсказки для тотема, щита, поломки предмета |
| `ClientPacketListener.handleEntityEvent` / `handleSoundEvent` | Клиентская сторона тех же событий |
| `DataComponents.CUSTOM_NAME` | Основа матчинга |
| `EntityBoundSoundInstance(SoundEvent, SoundSource, float, float, Entity, long)` | Сохранение привязки звука к сущности при подмене |
| `Entity.setCustomName` / `getCustomName` | Сам API жив (но см. регрессию ниже) |
| **Все события NeoForge**: `PlaySoundEvent`, `AttackEntityEvent`, `UseItemOnBlockEvent`, `PlayerInteractEvent`, `LivingEntityUseItemEvent`, `LivingUseTotemEvent`, `LivingShieldBlockEvent`, `LivingEquipmentChangeEvent`, `PlayerDestroyItemEvent`, `ArrowNockEvent`, `ArrowLooseEvent`, `ItemFishedEvent` | Весь слой хуков переносится как есть |
| `RegisterPayloadHandlersEvent`, `PayloadRegistrar` | Серверный компонент переносится как есть |

---

## Механические изменения

Правятся заменой сигнатур в миксинах и импортов — риска не несут.

| Было (1.21.1) | Стало (26.2) |
|---|---|
| `ClientLevel.playSeededSound(Player, …)` | `playSeededSound(**Entity**, …)` — обе перегрузки |
| `ServerLevel.playSeededSound(Player, …)` | `playSeededSound(**Entity**, …)` |
| `ServerLevel.levelEvent(Player, int, BlockPos, int)` | `levelEvent(**Entity**, int, BlockPos, int)` |
| `LevelRenderer.levelEvent(int, BlockPos, int)` | **переехал** в `net.minecraft.client.renderer.LevelEventHandler.levelEvent(int, BlockPos, int)` |
| `SoundEngine.play(SoundInstance)` → `void` | → возвращает `SoundEngine.PlayResult` |
| `world.entity.projectile.AbstractArrow` | `world.entity.projectile.**arrow**.AbstractArrow` |
| `world.entity.projectile.ThrownTrident` | `world.entity.projectile.**arrow**.ThrownTrident` |
| `world.entity.projectile.ThrowableItemProjectile` | `world.entity.projectile.**throwableitemprojectile**.ThrowableItemProjectile` |

---

## Регрессия: трезубец в полёте больше не опознаётся

В 1.21.1 конструктор `AbstractArrow` копировал `CUSTOM_NAME` предмета на саму сущность-снаряд, и
поскольку имя сущности синхронизируется, клиент мог опознать летящий «Мьёльнир» — при том что
`ItemStack` снаряда клиенту не передаётся. На этом держался весь резолв трезубца.

**В 26.2 этого копирования нет:** в байткоде `AbstractArrow` нет ни одного упоминания custom name.
Заодно исчез публичный `getPickupItemStackOrigin()` — остались `getWeaponItem()` (public) и
`getPickupItem()` (protected), но соответствующие поля на клиент по-прежнему не синхронизируются.

Значит для 26.2 нужен другой путь. Варианты по убыванию надёжности:

1. **Серверная подсказка.** Сервер знает стек снаряда точно. Наш протокол уже умеет ключи по
   сущности — достаточно добавить причину при спавне снаряда. Работает только с модом на сервере.
2. **Связывание при броске.** Клиент предсказывает бросок и знает предмет в руке; когда рядом
   появляется новый снаряд-сущность, запомнить связь «сущность → предмет» в трекере. Работает и на
   ванильном сервере, но это эвристика.
3. Попросить у NeoForge/Mojang синхронизацию — не вариант в наших сроках.

Разумно сделать 2 как базу и 1 как уточнение — ровно та же схема, что уже работает для остальных
звуков.

---

## Идентификаторы звуков: сверено

Из клиента 26.2 извлечено **1778** идентификаторов и сопоставлено с нашей таблицей (88 записей).
Результат неожиданно хороший: **все точные id на месте**, семейства тоже
(`item.armor.equip*` — 12 штук, `block.*.break` — 121, `block.*.hit` — 112, `block.*.place` — 120,
трезубец — 8, арбалет — 8, наковальня — 8).

Изменилось ровно одно: **козлий рог стал data-driven**. Идентификаторы `item.goat_horn.play*`
исчезли из `SoundEvents`, инструменты теперь описываются в `data/minecraft/instrument/*.json`, а
звук называется `item.goat_horn.sound.N`. Семейство в таблице расширено до префикса
`item.goat_horn.`, чтобы покрыть оба варианта.

## Что ещё предстоит сверить

- Слоты экипировки и API компонентов (`ItemProbe`) — в 1.21.2+ они заметно менялись.
- Поведение `PlaySoundEvent` относительно нового `PlayResult`.
- Работает ли связывание снаряда с предметом через `EntityJoinLevelEvent` (замена сломанного
  опознания трезубца) — проверяется только запуском.

## Влияние на документацию

`docs/PACK_GUIDE.md` написан под 1.21.1 и указывает `pack_format: 34`. Для 26.2 это **88**.
После порта руководство придётся ревизовать: формат пака, возможно часть id звуков, и оговорка про
трезубец.
