# 03 — Isoler la pièce

Avant de comparer quoi que ce soit, il faut savoir **quels pixels de la photo sont la pièce**. C'est l'étape qui
casse le plus souvent en conditions réelles ; elle a été revue après les premiers essais sur de vraies pièces.

## 0. Le réseau U²-Net-small (méthode principale)

Sur les photos réelles du propriétaire (table noire, plaid, tapis, pièces dont une partie est bleu nuit ou noire),
la segmentation par couleur ci-dessous coupait des morceaux de pièce, ou ne trouvait rien : le bord de l'image sert à
estimer la table, et une main, un jean ou un reflet suffisaient à faire exploser le seuil.

L'app utilise donc d'abord **U²-Net-small** (détection d'objet saillant, pré-entraîné, 4,6 Mo, ONNX,
`assets/segmenter.onnx`, ~100 ms sur CPU), qui n'a besoin d'aucune couleur de table (`PieceSegmenter`,
`OnnxSegmenter`). La carte de saillance (0 à 1, remise à la taille de la photo) est seuillée à 0,5, ouverte
(érosion puis dilatation), puis on garde la tache sous le centre et on remplit les trous, avec le même garde-fou de
surface (1 à 85 %). Sur 21 captures réelles de Famillez, 21 masques exploitables, y compris les parties sombres (une pièce à deux dents
sombres reste partiellement coupée). La version
complète du réseau (176 Mo) ne fait pas mieux. Si le modèle manque ou ne trouve aucune pièce, le matcher retombe sur la
segmentation par couleur, décrite ci-dessous.

## 1. La zone analysée

On n'analyse que le **carré centré** de côté égal à la moitié du petit côté de la photo (`PieceRecognizer.CROP = 0.5`).
Ce carré correspond à ce que l'utilisateur voit autour du cadre de visée, avec une marge de table.

> Erreur corrigée : la première version analysait 70 % de la photo dans chaque dimension, **plus large que l'écran**
> (l'aperçu caméra est rogné sur les côtés). La zone analysée contenait des choses invisibles pour l'utilisateur
> (sa main, d'autres pièces). Et la consigne « remplis le cadre » poussait à coller la pièce aux bords, là où
> l'algorithme lit la couleur de la table. La consigne est maintenant « garde de la table autour ».

L'image est ensuite réduite à 420 px de côté : une pièce qui occupe un tiers du cadre garde ~100 px de large, assez
pour lire ses côtés.

## 2. Netteté

Variance du **laplacien** de la luminance : une image nette a beaucoup de transitions brusques, une image floue
presque aucune. En dessous de `MIN_SHARPNESS = 12`, l'app demande de reprendre la photo.

## 3. La table comme une distribution

On prend une **bande de 5 %** sur le pourtour de l'image : c'est la table. Pour chaque canal Lab on mesure son
**centre** (médiane) et sa **dispersion** (écart absolu médian × 1,4826, plancher 3 pour L et 1,5 pour a et b).

L'écart d'un pixel à la table est une distance de **Mahalanobis** (diagonale) : chaque canal est divisé par sa propre
dispersion.

```
z = √( Σ ((canal − médiane) / dispersion)² )
```

Un plaid matelassé ou une table en bois varient beaucoup en **clarté** (ombres, veinage) mais très peu en **teinte**.
Une zone bleu foncé de la pièce, aussi sombre que le plaid, en reste donc très loin une fois chaque canal mis à
l'échelle.

## 4. Seuil

Un pixel est « pièce » si `z` dépasse à la fois **4** et **1,1 × le 99e centile** des `z` mesurés sur la bande de table.

> Erreur corrigée (photos réelles, pièce jaune et bleu foncé sur un plaid gris foncé) : la version précédente
> utilisait une distance de couleur unique et un seuil d'**Otsu**. Otsu sépare deux populations ; il a séparé le plaid
> de la partie **jaune** et laissé tomber le **bleu foncé** de la pièce. Un tenon disparaissait du masque, la forme
> lue devenait incohérente (deux côtés plats opposés), et la réponse dépendait du sens de la photo.

### Le repli par couleur, corrigé

Quand le réseau est absent, la table est modélisée par un **plan d'éclairage** par canal Lab (une lampe éclaire un côté
plus que l'autre), ajusté au moindre carré sur le bord **sans les intrus** (pixels à plus de 3,5 écarts du modèle :
main, jean, pli). Le seuil ne dépend plus d'un centile du bord. Les pixels très éloignés de la table (z > 4) servent de
**germe**, la pièce grandit ensuite vers les pixels peu éloignés (z > 2,5, **hystérésis**) mais seulement dans
l'**enveloppe convexe** de son germe (+ 6 px) : les zones sombres de l'image restent attachées sans avaler un
reflet de table voisin. La tache choisie est celle sous le centre, sinon la plus grande qui ne touche pas le bord.

> Mesuré sur 16 captures réelles : 9 « aucune pièce » avant, plus aucune après, 11 masques entiers.

## 5. Nettoyage du masque

1. **Ouverture** (érosion puis dilatation 3×3) : supprime les grains isolés.
2. **Fermeture** (2 dilatations puis 2 érosions) : recolle les morceaux d'une pièce séparés par une zone plus proche
   de la table.
3. **Composante sous le centre** : parmi les taches, on garde celle qui contient le centre de l'image (là où
   l'utilisateur a mis la pièce), sinon la plus grande.
4. **Remplissage des trous** : les zones de la pièce qui ressemblent à la table (un ciel beige sur une table beige)
   sont réintégrées si elles sont entourées par la pièce.
5. Garde-fous : la pièce doit couvrir entre 1 % et 85 % de l'image, sinon « aucune pièce détectée ».

## Limite connue

La méthode repose sur un **contraste de couleur entre la pièce et la table**. Une pièce grise sur une table grise
n'est pas séparable par la couleur seule ; l'app le dit au lieu d'inventer une réponse. Pistes : utiliser aussi les
contours (gradient), ou `grabCut` d'OpenCV (déjà embarqué) initialisé avec le cadre.
