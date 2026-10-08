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
| 40 pièces intérieures, un tiers à angle quelconque | **35/40** à une case près (87 %), rotation juste **35/35**, forme lue « intérieur » 40/40 |
| 4 coins (0°, 90°, 200°, 315°) | **4/4** dans le bon coin, avec la bonne rotation |
| 12 bords, angles quelconques | **12/12** lus comme bords, **12/12** sur la bordure, **12/12** à une case près avec la bonne rotation |
| photo très floue | « photo trop floue » |
| table vide | « aucune pièce détectée » |

Objectif du produit : > 70 % de suggestions correctes, < 3 s.

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

1. **Pas encore de jeu de photos réelles annotées.** L'app archive maintenant les 30 dernières captures et leur
   verdict sur le téléphone (`files/captures`) : c'est la matière pour mesurer et régler sur du réel.
2. **Contraste pièce / table** nécessaire ([03](03-segmentation.md)).
3. **Images répétitives** (ciel, eau) : la couleur seule ne suffit pas ; la confiance le signale. Pistes : texture
   (gradients orientés), puis éventuellement un modèle appris.
4. Pièces de formes non classiques (bords ondulés, pièces « fantaisie ») : la lecture de forme se désactive alors
   d'elle-même si elle est incohérente.
