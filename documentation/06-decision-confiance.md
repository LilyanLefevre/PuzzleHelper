# 06 — Décider et doser la confiance

## Classement et pistes alternatives

Chaque candidat autorisé reçoit sa meilleure distance sur les rotations permises. On trie, puis on retient la
meilleure piste et jusqu'à **3 alternatives**, en imposant au moins **1,5 case** entre deux pistes (suppression des
non-maxima) : proposer trois fois la même zone décalée d'une demi-case n'aiderait personne.

## Score de confiance

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
