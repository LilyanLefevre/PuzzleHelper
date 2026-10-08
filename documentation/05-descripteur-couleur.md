# 05 — Comparer les couleurs

## Espace Lab

Les couleurs sont converties de sRGB en **CIE Lab** : L = clarté, a = vert↔rouge, b = bleu↔jaune. Dans cet
espace, une même distance correspond à peu près à une même différence perçue, ce qui n'est pas le cas en RGB.

## Une grille 5 × 5 sur le corps de la pièce

La pièce a été **redressée** ([04](04-forme.md)) : on connaît l'orientation de ses côtés. On pose donc sur son corps
une **grille carrée de 5 × 5 cases**, alignée sur ces côtés, et on garde la couleur Lab moyenne de chaque case.

- Le côté de la grille vaut **0,9 ×** le côté d'un carré de même aire que la pièce. Pour un candidat de la boîte,
  c'est 0,9 × le côté d'une case. Les deux grilles couvrent donc la même portion d'image, quelle que soit la distance
  de prise de vue : le descripteur est **invariant à l'échelle**.
- Le facteur 0,9 garde la grille dans le corps et loin du contour (ombre, tranche en carton).
- Côté pièce, seuls les pixels du masque comptent ; une case presque vide (mortaise) est ignorée.

> Version précédente : un disque découpé en 17 zones (centre + 2 anneaux de 8 secteurs). Le disque couvrait mal les
> coins du corps carré ; la grille gagne **+6 points** sur photos réelles ([07](07-validation.md)).

## Rotations

Une fois la pièce redressée, il ne reste que **4 rotations** possibles. Tourner la pièce d'un quart de tour revient
à tourner la grille de 5 × 5 : aucun ré-échantillonnage d'image, la comparaison se fait sur 25 valeurs.

## Rendre la comparaison indépendante de la prise de vue

Trois corrections, appliquées **à l'identique** à la pièce et à chaque candidat de la boîte :

| Étape | Ce qu'elle retire | Pourquoi |
|---|---|---|
| Soustraire la couleur moyenne | le niveau global (exposition, balance des blancs) | l'appareil et l'impression ne rendent pas les mêmes couleurs |
| Retirer le **plan de clarté** `L = bx + cy` ajusté sur la grille | un dégradé linéaire de lumière | un éclairage de côté est dans le **sens de la photo**, pas de la pièce : sans cette étape, la réponse suivait le sens de prise de vue |
| Diviser chaque canal par sa dispersion | le contraste et la saturation | une photo est souvent plus terne ou plus vive que la boîte |

Ce qui reste est la **structure** des couleurs (« plus bleu en haut à gauche, plus clair au centre »), qui ne dépend
que du contenu imprimé.

## Distance

Pour un candidat et une rotation :

```
D = moyenne sur les cases valides de √(ΔL² + Δa² + Δb²)          (valeurs normalisées)
  + 0,02 × écart des couleurs moyennes
  + 0,05 × nombre de cases manquantes
```

La couleur moyenne garde un très faible poids : elle départage deux zones de même structure mais de teintes
différentes, sans pénaliser une photo mal balancée.

> Essai rejeté : ignorer les 20 % de cases les plus différentes (moyenne tronquée), pour absorber reflets et doigts.
> Sur photos réelles, **−9 points** : ces cases portent aussi l'information qui distingue deux endroits proches.
