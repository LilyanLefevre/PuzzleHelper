# 07 — Validation

## Générateur de pièces synthétiques (JVM)

`PieceMatcherTest` fabrique une « boîte » (90 taches de couleur floues + bruit) puis, pour une case donnée, une
**photo de pièce** réaliste :

- contour de puzzle classique : corps carré, tenons et mortaises circulaires, **côtés plats sur la bordure** ;
- le contenu des tenons vient de la case voisine, comme sur une vraie pièce ;
- tournée d'un angle **quelconque** (pas seulement 90°), agrandie ×6 ;
- posée sur une **table en bois** simulée (dégradé de lumière + veinage) ;
- couleurs atténuées (×0,88–0,92), léger flou optique puis **grain de capteur**.

## Résultats mesurés

Grille 21 × 14 (300 pièces), une analyse ≈ 0,3 s sur la JVM :


| Cas | Résultat |
|---|---|
| 40 pièces intérieures, un tiers à angle quelconque | **36/40** à une case près (90 %), rotation juste **36/36**, forme lue « intérieur » 39/40 |
| 12 pièces photographiées dans un sens puis retournées, lumière latérale | **23/24** correctes, même réponse dans les deux sens **12/12** |
| 4 coins (0°, 90°, 200°, 315°) | **4/4** dans le bon coin, avec la bonne rotation |
| 12 bords, angles quelconques | **12/12** lus comme bords, **12/12** sur la bordure, **12/12** à une case près avec la bonne rotation |
| photo très floue | « photo trop floue » |
| table vide | « aucune pièce détectée » |

Objectif du produit : > 70 % de suggestions correctes, < 3 s.

## Photos réelles : jeu public Puzzle-Map

[Puzzle-Map](https://huggingface.co/datasets/pablo-moreira/puzzle-map) (CC-BY-4.0, en partie issu de Roboflow
Universe) contient le puzzle « 120_avengers » (12 × 10) et **484 photos réelles** de ses 120 pièces, prises au
téléphone, chacune annotée avec sa ligne, sa colonne, son quart de tour et le type de ses 4 côtés.

Les pièces y sont **tenues à la main** devant un décor encombré : ce jeu ne teste pas la segmentation sur table.
`DatasetReplayTest` utilise le cadre annoté (rétréci de 16 % pour ne garder que le corps) comme masque, et mesure la
**comparaison de couleurs** et l'apport des **contraintes de forme** (types de côtés annotés).

Reconstruire le banc (macOS ; un dossier par puzzle) :

```bash
python3 tools/dataset/prepare_puzzle_map.py /tmp/puzzle-map
PUZZLE_DATASET_DIR=/tmp/puzzle-map ./gradlew testDebugUnitTest --tests '*DatasetReplayTest' -i
```

Résultats (bonne case à ±1, hasard = 1/120) :

| Version du descripteur | Couleur seule | + côtés plats | Bonne case parmi les 4 pistes | Rotation juste |
|---|---|---|---|---|
| disque 17 zones, centrage seul | 37 % | 52 % | 86 % | 89 % |
| + normalisation contraste/saturation | 46 % | 61 % | 87 % | 85 % |
| **grille 5 × 5** | 52 % | 66 % | 89 % | 89 % |
| grille 5 × 5 + moyenne tronquée *(rejeté)* | 45 % | 57 % | 87 % | 87 % |
| grille 6 × 6 *(pas mieux)* | 52 % | 66 % | 88 % | 90 % |
| **grille 5 × 5 + retrait du plan de clarté** (version actuelle) | 51 % | **65 %** | **89 %** | **88 %** |

La dernière ligne coûte 1 point ici mais rend la réponse indépendante d'un éclairage de côté (test synthétique
« lumière latérale » : même réponse dans les deux sens 12/12, contre 9/12 sans).

Lecture : avec la forme, **2 fois sur 3 la meilleure piste est la bonne**, et **9 fois sur 10 la bonne est dans les
4 pistes proposées**. On est encore sous l'objectif de 70 % pour la meilleure piste seule.

### Tous les puzzles annotés du jeu

Le script prépare désormais les 7 puzzles dont les photos portent une position (un dossier par puzzle). Ici la rotation
est comptée sur **toutes** les photos, pas seulement celles bien placées ; « exacte » = la bonne case.

| Puzzle | Photos | Hasard | Case exacte, couleur seule | Case exacte, + forme | Dans les 4 pistes | Rotation |
|---|---|---|---|---|---|---|
| 120_avengers (12 × 10) | 484 | 0,8 % | 44 % | **57 %** | 88 % | 68 % |
| 100_dc (10 × 10) | 13 | 1 % | 7 % | 30 % | 53 % | 38 % |
| 5 × 6_patos (2 × 3) | 146 | 17 % | 21-52 % | **43-75 %** | 100 % | 43-75 % |
| **Total** | **643** | | 42 % | **56 %** | **90 %** | 64 % |

La rotation est le point faible sur ces photos tenues à la main : à creuser.

## Banque d'images : 24 vraies images, 576 pièces

Un seul puzzle réel ne dit pas comment l'algorithme se comporte sur d'autres contenus. `ImageBankBenchmarkTest`
prend **24 vraies images** (8 peintures du domaine public, 16 photos sous licence Unsplash), en fait des puzzles de
500 pièces, et « photographie » 24 pièces par image (les 4 coins, 6 bords, 14 intérieures) avec des conditions
tirées au hasard : angle quelconque, lumière latérale d'intensité et de direction variables, balance des blancs,
exposition, table (bois clair, bois foncé, blanc, gris, feutre vert). **Toute la chaîne** tourne : isolement sur la
table, lecture de la forme, couleur.

Chaque image reçoit un indice d'**aplat** : la part de cases presque unies (ciel, mer, fonds), le cas difficile attendu.

| Type d'image | Pièces | Pièce trouvée | Case exacte | Dans les 4 pistes | Rotation | Forme lue |
|---|---|---|---|---|---|---|
| Texturée (< 15 % d'aplats) | 240 | 100 % | **75 %** | 85 % | 86 % | 97 % |
| Mixte (15-40 %) | 144 | 98 % | 63 % | 76 % | 77 % | 94 % |
| Beaucoup d'aplats (> 40 %) | 192 | 98 % | **51 %** | 68 % | 74 % | 95 % |
| **Total** | **576** | 99 % | **64 %** | 77 % | 80 % | 96 % |

Ce qui ressort :
- l'objectif de 70 % est **atteint sur les images texturées** (peintures, scènes chargées) ;
- la **couleur seule ne suffit pas sur les aplats** : ciel et mer tombent à ~50 %. Prochaine piste : la texture
  (gradients orientés) et la position relative (une pièce de ciel est en haut) ;
- la **forme est fiable** (96 %) et l'isolement sur table aussi (99 %), y compris sur bois foncé ou feutre vert.

Reconstruire et lancer (macOS) :

```bash
python3 tools/dataset/prepare_image_bank.py /tmp/image-bank
PUZZLE_IMAGES_DIR=/tmp/image-bank ./gradlew testDebugUnitTest --tests '*ImageBankBenchmarkTest' -i
```

Les tirages sont déterministes (graine = nom de l'image) : les chiffres sont reproductibles d'un poste à l'autre.

## Photos réelles : captures de l'app

`RealPhotoReplayTest` rejoue des captures réelles récupérées sur le téléphone (archive `files/captures`), avec l'image
de la boîte et la grille du projet. Il est ignoré si `PUZZLE_REAL_DIR` n'est pas défini, et écrit pour chaque photo une
image du masque obtenu.

Premier cas réel (Klimt, *La Dame à l'éventail*, 27 × 37) : une pièce de bord jaune et bleu foncé, photographiée deux
fois sur un plaid gris foncé (côté plat en haut, puis à droite).

| Version | Masque | Forme lue | Réponse |
|---|---|---|---|
| distance unique + Otsu | tenon bleu manquant | incohérente (2 plats opposés) | dépend du sens de la photo |
| Mahalanobis + fermeture | pièce entière (photo 2), presque entière (photo 1) | **bord**, correcte sur les 2 photos | **bordure gauche** dans les 2 sens |

La position le long du bord et la confiance restent faibles : il faut un jeu de photos réelles **annotées** pour
régler la comparaison des couleurs.

## Sur appareil

- `PieceRecognizerDeviceTest` : décodage de vrais JPEG, préparation de la boîte et analyse sous 3 s, cas d'échec.
- `ScanFlowTest` : parcours complet dans l'app (liste → table → scan → résultat → pistes → fermeture, photo floue,
  table vide, viseur), avec captures d'écran.
- La CI rejoue tout sur un émulateur Android 14 à chaque push.

## Leçons apprises

- **Les images de test doivent ressembler à de vraies photos.** Un patch synthétique lisse agrandi ×6 tombe pile sur
  le seuil de netteté et fait basculer les tests selon l'appareil. Grain de capteur et flou réaliste règlent ça.
- **La zone analysée doit être celle que l'utilisateur voit** ([03](03-segmentation.md)).
- **Lancer `connectedDebugAndroidTest` sur un vrai téléphone désinstalle l'app et efface ses données.** Sur
  téléphone : `installDebug installDebugAndroidTest` puis `adb shell am instrument`.

## Limites et prochaines étapes

1. **Photos réelles sur table, annotées.** Le jeu public couvre la couleur et la forme, pas la segmentation sur
   table. L'app archive les 200 dernières captures et leur verdict (`files/captures`) pour en constituer un.
2. **Contraste pièce / table** nécessaire ([03](03-segmentation.md)).
3. **Images répétitives** (ciel, eau) : la couleur seule ne suffit pas ; la confiance le signale. Pistes : texture
   (gradients orientés), puis éventuellement un modèle appris.
4. Pièces de formes non classiques (bords ondulés, pièces « fantaisie ») : la lecture de forme se désactive alors
   d'elle-même si elle est incohérente.
