# GolfMatch

Appli mobile de saisie de score de golf, pensée d'abord pour le **match play** sur les petits parcours (compacts, 9 trous, départs en shotgun).

- Joueurs désignés par un code court (CHCU, DANO…), parties de 1 à 4 joueurs.
- Match play (1 contre 1, 1 contre 2, 2 contre 2 en scramble, meilleure balle ou foursome) ou stroke play brut.
- Parcours de 9, 9+9 ou 18 trous, départ sur n'importe quel trou.
- Carte de score, état du match en direct, envoi du résultat par mail ou message.

Le détail fonctionnel est dans [docs/cahier-des-charges-v1.md](docs/cahier-des-charges-v1.md).

## Technique

Kotlin Multiplatform : la logique métier (calcul du match play, classements, coups rendus) est partagée ; les écrans sont natifs, SwiftUI sur iPhone (puis Apple Watch) et Jetpack Compose sur Android.

## Structure

- `shared/` : logique partagée en Kotlin (codes joueurs, parcours et ordre des trous, match play, stroke play), avec ses tests.
- `iosApp/` (à venir) : écrans SwiftUI.
- `androidApp/` (à venir) : écrans Jetpack Compose.

Lancer les tests de la logique partagée :

```sh
./gradlew :shared:jvmTest
```

## État

Cahier des charges V1 validé, logique partagée en place et testée. Écrans iPhone à venir.

## Licence

[MIT](LICENSE)
