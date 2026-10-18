# EterSleep

**Passer la nuit sans attendre tout le monde** : il suffit qu'une part des joueurs **actifs** soit au lit. Les joueurs AFK,
invisibles ou spectateurs ne bloquent plus la nuit. Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.18.0+** (`depend`) : langue, activité des joueurs (`Activity`, AFK), joueurs invisibles (`Vanish`).

## Fonctionnement (`SleepService`)

- **Comptés** dans un monde : les joueurs au lit (toujours, même immobiles ou avec `etersleep.ignore` : le staff a souvent
  `*` dans LuckPerms), et les autres s'ils ne sont pas AFK (`EterLib/config.yml > activity.afk-minutes`, 5 par défaut) ni
  `etersleep.ignore`. Jamais : les invisibles (ni comptés ni annoncés), les spectateurs.
- **Nécessaires** : `percentage` % des comptés (arrondi au-dessus), au moins 1. Ex : 50 % et 3 joueurs actifs → 2 au lit.
- **Affichage** : à chaque coucher, « X est au lit (2/3) » dans le monde ; tant que quelqu'un est au lit, la barre d'action
  « ☾ 2/3 au lit pour passer la nuit » (toutes les demi-secondes).
- **La nuit passe** (la nuit, ou pendant un orage) : elle défile de `skip-speed` ticks par tick (100 : environ 5 s), puis au
  matin la pluie s'arrête, les joueurs couchés se lèvent et leur compteur de sommeil est remis à zéro (pas de phantoms).
  Le temps du monde avance vraiment (`setFullTime`) : le compteur de jours et les phases de lune suivent.
- **Règle de Minecraft** : `playersSleepingPercentage` est mise à 101 dans les mondes gérés (au démarrage et à chaque monde
  chargé ensuite) : c'est EterSleep qui décide, Minecraft ne passe plus la nuit de lui-même.

## Configuration (`config.yml`)

| Clé | Par défaut | Rôle |
|---|---|---|
| `percentage` | 50 | Part des joueurs comptés qui doivent être au lit (1 à 100) |
| `skip-speed` | 100 | Vitesse de la nuit qui défile (ticks de jeu par tick, 10 au moins) |
| `worlds` | `[]` | Mondes gérés ; vide : tous les mondes normaux |

## Permissions

| Permission | Par défaut | Effet |
|---|---|---|
| `etersleep.ignore` | personne | Éveillé, ne bloque jamais la nuit ; au lit, compte comme les autres (staff, comptes de test) |
