# 05 — Comparer les couleurs

## Espace Lab

Les couleurs sont converties de sRGB en **CIE Lab** : L = clarté, a = vert↔rouge, b = bleu↔jaune. Dans cet
espace, une même distance correspond à peu près à une même différence perçue, ce qui n'est pas le cas en RGB.

## Le descripteur à 17 zones

On découpe un **disque** autour du centre de la pièce (ou du candidat) en 17 zones :

```
        anneau extérieur : 8 secteurs (0,65 r – 0,92 r)
        anneau intérieur : 8 secteurs (0,35 r – 0,65 r)
        centre           : 1 disque   (0 – 0,35 r)
```

et on garde la **couleur Lab moyenne** de chaque zone.

- `r` est le **rayon équivalent** : `√(aire / π)`. Pour la pièce, l'aire du masque ; pour un candidat, l'aire d'une
  case. Les deux disques couvrent donc la même portion d'image, quelle que soit la distance de prise de vue :
  le descripteur est **invariant à l'échelle**.
- On s'arrête à 0,92 r pour rester dans le corps de la pièce et éviter son contour (ombre, tranche en carton).
- Côté pièce, seuls les pixels du masque comptent.

## Invariance à l'éclairage

On soustrait à chaque zone la **moyenne des 17 zones**. La comparaison porte alors sur la *structure* des couleurs
(« plus bleu en haut, plus vert à droite ») plutôt que sur leur niveau absolu, qui change avec la lumière, la balance
des blancs et l'impression. La couleur moyenne garde un petit poids séparé (voir ci-dessous).

## Rotations

La pièce a déjà été redressée de `θ` ([04](04-forme.md)) en décalant l'angle de chaque pixel avant de l'affecter à
un secteur. Une rotation d'un quart de tour revient alors à **décaler les secteurs de 2 crans** (8 secteurs = 45°
chacun) : on compare la zone `j` de la pièce à la zone `j − k` du candidat, pour `k ∈ {0, 2, 4, 6}`. Aucun
ré-échantillonnage d'image n'est nécessaire.

## Distance

Pour un candidat et un décalage `k` :

```
D = moyenne sur les zones valides de √(0,6·ΔL² + Δa² + Δb²)
  + 0,25 × écart des couleurs moyennes (a,b pleins, L à 30 %)
  + 0,4 × nombre de zones manquantes
```

- Une zone est valide si elle contient au moins 2 pixels des deux côtés (une mortaise peut vider un secteur).
- Le dernier terme pénalise les comparaisons sur trop peu de zones ; en dessous de 8 zones communes la comparaison
  est rejetée.
