# GolfMatch – cahier des charges V1

Appli mobile de saisie de score en match play, conçue pour un club compact 9 trous par 3, d'abord sur iPhone puis sur Android.

Choix technique : Kotlin Multiplatform, avec la logique partagée et des écrans natifs (SwiftUI sur iPhone et Apple Watch, Jetpack Compose sur Android). Projet open source, licence MIT. Installation sur iPhone via Xcode avec un Apple ID gratuit (réinstallation tous les 7 jours, données conservées).

## Joueurs
- Fiche : prénom, nom, index, code de 2 à 4 lettres (modifiable), couleur, e-mail et téléphone facultatifs.
- L'index est enregistré mais pas utilisé en V1.
- Pas d'équipes enregistrées : un camp de deux est composé à la création du match.
- Partout dans l'appli, joueurs et camps sont désignés par leurs codes (« CHCU », « CHCU & DANO »).

Règle de proposition du code (en majuscules, sans accents) :
- prénom simple : ses 2 premières lettres (Christophe → CH) ;
- prénom composé : la première lettre de chaque prénom (Jean-Pierre → JP) ;
- puis les 2 premières lettres du nom (Cuq → CU).

Exemples : Christophe Cuq → CHCU, Jean-Pierre Martin → JPMA.

Le code est unique. Si le code proposé est déjà pris, l'appli propose directement une variante libre :
1. la 1re lettre du nom suivie de sa 3e, puis de sa 4e, etc. (Cuq → CHCQ) ;
2. si le nom est épuisé, même principe sur le prénom simple : 1re lettre suivie de la 3e, puis de la 4e, etc. (Christophe → CRCU) ;
3. si aucune variante n'est libre, l'appli le signale et la fiche ne peut pas être enregistrée tant que le code n'est pas modifié à la main.

Un code saisi à la main et déjà pris est refusé de la même façon.

## Parcours
- Un seul club en V1, mais trois types de parcours prévus dès maintenant :
  - 9 trous : une seule boucle ;
  - 9+9 : deux boucles de 9 trous nommées (club actuel : Rouge et Jaune, noms modifiables car propres à ce club) ;
  - 18 trous : un parcours de 18 trous classique.
- Pour chaque trou : par et classement HCP. Pour chaque parcours, saisis à sa création : Slope et SSS, par boucle de 9 trous pour un 9 ou un 9+9, et pour le 18 trous complet ainsi que l'aller et le retour pour un 18 trous. Pour une partie de 18 trous sur deux boucles de 9, les valeurs se déduisent selon la règle WHS : SSS additionnés, Slope moyennés. Valeurs facultatives, non utilisées en V1.
- Le classement HCP est unique : une même valeur ne peut pas être attribuée à deux trous (1 à 9 sur un 9 trous, 1 à 18 sur un 9+9 ou un 18 trous).
- Le classement HCP n'est pas utilisé en V1 ; il servira aux coups rendus. Règles de classement sur 18 quand une boucle de 9 est jouée deux fois : un trou classé n sur la boucle (1 à 9) reçoit 2n − 1 au premier passage et 2n au second. Sur un 9+9 joué R+R ou J+J, les trous de la boucle sont d'abord reclassés de 1 à 9 selon leur classement sur 18, puis la même règle s'applique.

## Création d'un match
- Créé au moment de jouer, horodaté automatiquement.
- De 1 à 4 joueurs, avec ou sans équipes : joueur seul (score en coups, sans match play), 1 contre 1, 1 contre 2, 2 contre 2. Match play quand les joueurs sont répartis en deux camps (1 contre 1, 1 contre 2, 2 contre 2). Sans camps, de 2 à 4 joueurs : stroke play brut, avec classement aux coups (le net viendra plus tard). À 2 joueurs, on choisit à la création entre 1 contre 1 en match play et chacun pour soi en stroke play.
- Départ en shotgun : on indique le trou de départ (ex. « départ du 3 rouge »). La saisie commence à ce trou et fait le tour de la boucle (3 à 9, puis 1 et 2). Sur un parcours en deux boucles de 9 trous, le second tour repart du même numéro de trou sur sa boucle (ex. 3 jaune). Sur un parcours de 18 trous, l'ordre est 3 à 18, puis 1 et 2, et la partie s'arrête là.
- Pour tout camp de deux joueurs, formule choisie au départ : scramble (un score d'équipe par trou), meilleure balle (score de chaque joueur, meilleur retenu) ou foursome (une balle jouée alternativement).
- Composition de la partie selon le type de parcours. Sur un 9 trous : un ou deux tours de la boucle. Sur un 18 trous : 18 trous, ou 9 aller (1 à 9), ou 9 retour (10 à 18). Sur un 9+9, deux sélecteurs :
  - premier tour : Rouge / Jaune ;
  - second tour : Rien / Rouge / Jaune ;
  - combinaisons possibles : R, J, R+J, J+R, R+R, J+J.

## Pendant la partie
- Saisie sur un seul téléphone, trou par trou, en nombre de coups.
- Calculs en brut uniquement, en match play comme en stroke play.
- État du match affiché en permanence : égalité, x Up, dormie, match gagné x & y.
- Une fois le match joué, les trous restants peuvent être saisis pour le total de coups.

## Fin de partie
- Résultat (match play, ou classement en stroke play) et carte de score : coups trou par trou, totaux par tour et total général, écart au par.
- Envoi facultatif par mail ou par message aux joueurs dont les coordonnées sont renseignées : texte (résultat et scores trou par trou) accompagné d'une image de la carte.
- Historique de toutes les parties.

## Sauvegarde
- Données stockées sur le téléphone et incluses dans sa sauvegarde (iCloud sur iPhone).
- Export et import manuels d'un fichier de sauvegarde.
- Synchronisation iCloud (CloudKit) possible uniquement avec le compte développeur Apple payant.

## Versions ultérieures
- Stableford et calculs en net.
- Coups rendus selon l'index et le classement HCP (WHS) : handicap de jeu = Index × Slope ÷ 113 + (SSS − Par), avec la moitié de l'index sur 9 trous ; coefficient selon la formule (simple 100 %, meilleure balle 90 %, foursome 50 % du total de l'équipe, scramble à 2 : 35 % du plus bas + 15 % du plus haut) ; le camp le plus bas joue à 0, l'autre reçoit la différence, répartie selon le classement HCP (un coup sur les trous classés 1 à n, un second au-delà de 18).
- Balle retenue à chaque coup en scramble.
- Plusieurs clubs, avec nommage des boucles propre à chaque club.
- Statistiques.
- Apple Watch.
