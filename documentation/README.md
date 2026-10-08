# Documentation PuzzleIt

PuzzleIt dit **où va une pièce de puzzle** sur l'image de la boîte, à partir d'une simple photo de la pièce,
sans réseau et sans apprentissage : uniquement de la géométrie et de la couleur.
Ces pages expliquent la science derrière, étape par étape, et ce qui est vérifié par les tests.

| Page | Contenu |
|---|---|
| [01 — Vue d'ensemble](01-vue-ensemble.md) | le problème, la chaîne complète de la photo à la suggestion |
| [02 — La boîte et sa grille](02-boite-et-grille.md) | image de référence, prédécoupage virtuel, positions candidates |
| [03 — Isoler la pièce](03-segmentation.md) | recadrage, couleur de la table, seuil d'Otsu, nettoyage du masque |
| [04 — Lire la forme](04-forme.md) | redresser la pièce, côtés plats / tenons / mortaises, coins et bords |
| [05 — Comparer les couleurs](05-descripteur-couleur.md) | espace Lab, grille 5 × 5, éclairage, rotations |
| [06 — Décider et doser la confiance](06-decision-confiance.md) | classement, pistes alternatives, score de confiance, précision adaptative |
| [07 — Validation](07-validation.md) | pièces synthétiques, 484 photos réelles (Puzzle-Map), résultats, limites |

Code de référence : [`PieceMatcher.kt`](../app/src/main/java/com/lilyan_lefevre/puzzleit/feature/recognition/PieceMatcher.kt)
(Kotlin pur, testable sur JVM) et [`PieceRecognizer.kt`](../app/src/main/java/com/lilyan_lefevre/puzzleit/feature/recognition/PieceRecognizer.kt)
(lien avec les fichiers et les bitmaps Android).
