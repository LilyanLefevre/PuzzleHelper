# 06 — Décider et doser la confiance

## Classement et pistes alternatives

Chaque candidat autorisé reçoit sa meilleure distance sur les rotations permises. On trie, puis on retient la
meilleure piste et jusqu'à **3 alternatives**, en imposant au moins **1,5 case** entre deux pistes (suppression des
non-maxima) : proposer trois fois la même zone décalée d'une demi-case n'aiderait personne.

**Aucune grille saisie.** La taille d'une pièce est déduite du **nombre de pièces** (imprimé sur la boîte) et de la forme
de l'image ; les lignes × colonnes saisies à la création ne servent plus : personne ne les connaît avant d'avoir fini
le tour du puzzle. Les candidats sont posés tous les demi-pas d'une pièce, puis la place des meilleures pistes est
**affinée** : le réseau regarde 25 carrés autour du point (pas de 0,125 pièce) et la place devient le centre pondéré
par leur similarité (`PieceMatcher.refine`). Erreur moyenne de position sur tes photos réelles retrouvées : 0,19 → 0,08 pièce.

## Score de confiance avec le réseau (cas normal)

Chaque candidat a un **score fusionné** `s = z(similarité du réseau) − 0,5 · z(distance couleur)`. Les 8 meilleures pistes
(après suppression des non-maxima) se partagent 100 % par un softmax, `confiance_i = 100 · e^(s_i/T) / Σ e^(s_j/T)`, avec
`T = 0,5` (`T = 0,7` pour le re-classement des pistes couleur quand l'index de la boîte n'est pas encore prêt). La confiance
suit donc **toujours l'ordre des pistes** (la piste 1 n'est jamais moins confiante que la piste 2) et leur somme ne dépasse pas 100.

`T` est calibré sur les scans des benchmarks (`PUZZLE_CALIB=1` exporte le score et l'exactitude de chaque piste). Fiabilité de
la première piste à `T = 0,5` :

| Confiance annoncée | Puzzle-Map + banque (1 217 scans) : justes | Captures réelles du propriétaire (23 scans) : justes |
|---|---|---|
| < 20 % | 9 % | 0 sur 1 |
| 20 – 40 % | 64 % | 3 sur 10 |
| 40 – 60 % | 80 % | 0 sur 6 |
| 60 – 80 % | 96 % | 2 sur 3 |
| ≥ 80 % | 96 % | 3 sur 3 |

Les jeux de référence sont **sous-confiants** et les photos réelles **sur-confiantes** : `T = 0,5` est un compromis (l'optimum
est 0,35 sur les jeux de référence, 0,5 à 0,7 sur les photos réelles). Le jeu réel est petit (23 scans, 9 pièces) : lisez-le
comme un avertissement de ne pas sur-promettre, pas comme une mesure.

## Score de confiance sans réseau (repli complet)

Deux questions, qui doivent être vraies toutes les deux :

| Question | Mesure |
|---|---|
| La meilleure piste est-elle nettement meilleure qu'**un endroit au hasard** ? | `spread = (D_médiane − D₁) / D_médiane` |
| Est-elle nettement meilleure que **la deuxième piste distincte** ? | `margin = (D₂ − D₁) / D₂` |

```
confiance = 100 × spread × min(1, 6 × margin)      (bornée à 99)
```

Une image très répétitive (ciel uni) donne un `spread` faible ; deux endroits presque identiques donnent une `margin`
faible. Dans les deux cas la confiance baisse, ce qui est le comportement voulu.

Les pistes alternatives reçoivent une confiance dérivée : celle de la meilleure, multipliée par leur propre `spread`
rapporté à celui de la meilleure. Une alternative n'est donc jamais plus confiante que la meilleure (vérifié par
les tests).

## Précision adaptative

| Confiance | Ce que l'app montre |
|---|---|
| ≥ 55 | la **case** précise (« Ligne 7 · Colonne 10 ») |
| 30 – 54 | une **zone** de 3 × 3 cases (« Zone : en haut à gauche ») |
| < 30 | une **région** d'un tiers du puzzle, signalée comme fragile |

C'est le principe du produit : aider sans tricher, et ne jamais afficher une précision qu'on n'a pas.
