---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish']
inputDocuments: ['/Users/lilyan/Documents/Projets/PuzzleHelper/_bmad-output/analysis/brainstorming-session-2026-01-15.md']
workflowType: 'prd'
documentCounts:
  briefCount: 0
  researchCount: 0
  brainstormingCount: 1
  projectDocsCount: 0
classification:
  projectType: mobile_app
  domain: scientific
  complexity: medium
  projectContext: greenfield
---

# Product Requirements Document - PuzzleHelper

**Author:** Lilyan
**Date:** 2026-01-19T19:44:00.000Z

## Success Criteria

### User Success

- **Moment "Aha!" :** "Ouah j'avais pas ça c'est évident" lors du premier déblocage par l'application
- **Key Metric :** +1 puzzle terminé par mois (significatif vu le temps investi dans les puzzles complexes)
- **User Journey :** Scan boîte → Début puzzle → Blocage → Aide application → Photo finale + statistiques de complétion
- **Emotional Success :** Élimination de la frustration tout en maintenant l'autonomie et le plaisir du puzzle

### Business Success

- **3 Months :** Usage personnel régulier + premières installations depuis le store
- **12 Months :** 100 utilisateurs actifs mensuels ("ce serait fou déjà")
- **Vision Goal :** Plateforme communautaire avec gamification et classements
- **Growth Strategy :** Bouche-à-oreille initial → Features communautaires → Effet réseau

### Technical Success

- **Accuracy Target :** >70% de suggestions correctes ("pas mal" comme objectif initial)
- **Performance :** Reconnaissance et comparaison de pièce en temps acceptable sur Android
- **Algorithm Success :** Prédécoupage intelligent + reconnaissance fiable + suggestions pertinentes
- **Reliability :** Fonctionnement offline pour les algorithmes de base

### Measurable Outcomes

- **User Engagement :** Temps moyen de complétion réduit de 30%+
- **Retention :** 60%+ d'utilisateurs terminent au moins 1 puzzle supplémentaire par mois
- **Satisfaction :** 4.0/5.0+ rating sur store avec feedback positif sur moments "aha!"
- **Technical Performance :** <3 secondes pour reconnaissance et suggestion de position

## Product Scope

### MVP - Minimum Viable Product

- **Core Features :**
  - Scan boîte puzzle + prédécoupage intelligent en N pièces virtuelles
  - Reconnaissance pièce par photo (crop 70% centre, 4 rotations testées)
  - Suggestions de position (zone précise ou générale selon confiance)
  - Statistiques de base (temps de complétion, date de fin)
- **Technical Approach :** Algorithme de comparaison pixel/histogramme sans détection zones complétées
- **Platform :** Android uniquement pour v1

### Growth Features (Post-MVP)

- **Advanced Detection :** Détection automatique des zones déjà complétées
- **Smart Suggestions :** Élimination des zones remplies + suggestions contextuelles avancées
- **Enhanced UX :** Interface améliorée avec historique et progression
- **Performance :** Algorithmes optimisés + IA optionnelle pour précision accrue

### Vision (Future)

- **Community Platform :**
  - Classements de puzzles terminés (mensuel/annuel)
  - Statistiques comparatives entre amis
  - Gamification : badges, défis, temps moyen par type de puzzle
  - Partage de réussites et moments "aha!"
- **AI Integration :** Algorithmes d'apprentissage automatique pour reconnaissance améliorée
- **Cross-Platform :** iOS + Web pour communauté élargie
- **Advanced Features :** Mode collaboratif, puzzles personnalisés, intégration réseaux sociaux

## User Journeys

### Journey 1: Marc - L'Utilisateur Principal Passionné

**Persona :** Marc, 45 ans, ingénieur logiciel, passionné de puzzles complexes depuis 10 ans

**Scène d'Ouverture :**
Marc est dans son salon, frustré. Pour la 3ème fois ce mois-ci, il abandonne un puzzle de 2000 pièces - une magnifique scène de forêt automnale avec des milliers de feuilles similaires. Il passe 2 heures à essayer de placer 15 pièces rouges/orangées qui se ressemblent toutes. "C'est impossible", pense-t-il en rangeant tout. Ce soir-là, sur son canapé, il ouvre le Play Store et cherche "puzzle help application".

**Parcours Émotionnel :**
*Téléchargement* → *Curiosité* → *Scan de la boîte* → *Premier test sur pièce difficile* → **"OUAH ! J'avais pas vu ça c'est évident !"** → *Soulagement* → *Confiance* → *Plaisir renouvelé* → *Puzzle terminé avec fierté*

**Résolution :**
Marc termine son puzzle en 3 jours au lieu de 2 semaines. Il se sent intelligent, pas assisté. Il recommande l'app à son club de puzzles local et devient un ambassadeur naturel.

### Journey 2: Lilyan - La Créatrice Utilisatrice

**Persona :** Lilyan, créatrice de PuzzleHelper, utilise son app 1-2 fois par mois pour puzzles de 1000+ pièces

**Scène d'Ouverture :**
Lilyan commence un nouveau puzzle de 1000 pièces - un défi architectural complexe. Elle connaît parfaitement son application mais veut la tester en conditions réelles pour identifier les améliorations possibles.

**Parcours Émotionnel :**
*Test conscient* → *Validation d'algorithmes* → *Amélioration itérative* → *Satisfaction personnelle* → *Idées pour nouvelles features* → *Orgueil de création utile*

**Résolution :**
Lilyan non seulement termine ses puzzles plus rapidement, mais elle améliore continuellement son application basée sur son expérience réelle, créant un cycle d'innovation vertueux.

### Journey 3: Support - Gestion des Cas Limites et Récupération

**Persona :** Équipe support et système de gestion des erreurs

**Scène d'Ouverture :**
Un utilisateur signale "l'app ne reconnaît pas ma pièce". Le système doit diagnostiquer et guider vers la résolution.

**Parcours Émotionnel :**
*Frustration utilisateur* → *Détection automatique du problème* → *Guidance pas-à-pas* → *"Prenez une nouvelle photo"* → *Résolution* → *Confiance restaurée* → *Feedback positif*

**Cas Limites Identifiés :**
- **Photo floue :** Détection automatique + demande nouvelle photo avec conseils d'éclairage
- **Puzzle endommagé :** Message clair sur les limites de reconnaissance
- **Mauvais éclairage :** Suggestions d'amélioration et recalibrage
- **Pièce atypique :** Fallback vers suggestions de zone plutôt que position précise

### Journey 4: Sarah - La Nouvelle Utilisatrice

**Persona :** Sarah, 28 ans, reçoit un puzzle complexe en cadeau, jamais fait de puzzles de plus de 500 pièces

**Scène d'Ouverture :**
Sarah ouvre son cadeau - un magnifique puzzle de 1500 pièces représentant une carte du monde ancienne. Elle est intimidée mais excitée. Son ami lui recommande PuzzleHelper.

**Parcours Émotionnel :**
*Intimidation* → *Découverte de l'app* → *Premier "aha!"* → *Confiance croissante* → *Addiction saine* → *Communauté* → *Partage de réussites*

**Résolution :**
Sarah devient une utilisatrice régulière, rejoint la future communauté, et recommande l'app à d'autres débutants intimidés par les puzzles complexes.

### Journey Requirements Summary

**Capacités Révélées par les Journeys :**

**Onboarding Intuitif :**
- Tutoriel rapide pour nouveaux utilisateurs (Sarah)
- Mode avancé pour experts (Marc)
- Mode créateur pour développeurs (Lilyan)

**Gestion Robuste des Erreurs :**
- Détection automatique de photo floue
- Guidance pas-à-pas pour résolution
- Messages clairs sur limites et contraintes
- Fallback intelligent vers suggestions de zone

**Expérience Personnalisée :**
- Adaptation au niveau d'expertise
- Historique et progression
- Mode "créateur" avec outils de diagnostic
- Feedback et amélioration continue

**Support et Communauté :**
- Système de support intégré
- Partage de réussites et moments "aha!"
- Fonctionnalités sociales futures (classements, défis)
- Boucle de feedback pour améliorations

## Domain-Specific Requirements

### Compliance & Regulatory

**Scientific Validation Requirements:**
- Tests unitaires obligatoires avec puzzles de référence pour validation algorithmique
- Reproductibilité exigée : même photo + même pièce = même suggestion (qualité photo égale)
- Métriques de performance : score de confiance sur 100 (probabilité de précision)
- Documentation transparente des décisions algorithmiques

### Technical Constraints

**Performance Requirements:**
- <10 secondes maximum pour analyse CV et reconnaissance sur Android
- Usage ressources optimisé : nécessaire mais pas excessivement gourmand
- Fonctionnement 100% offline : aucune dépendance à connexion internet

**Algorithm Validation:**
- Transparence basée sur motif de pièce : évident et compréhensible pour utilisateur
- Historique des suggestions conservé pour analyse et debug
- Logs de décision pour amélioration continue

### Integration Requirements

**Data Management:**
- Stockage local des puzzles et analyses (confidentialité utilisateur)
- Sauvegarde progression et statistiques de complétion
- Export possible des données utilisateur (portabilité)

**Platform Integration:**
- Accès caméra Android avec permissions appropriées
- Stockage fichiers locaux avec gestion espace
- Notifications optionnelles pour rappels et progression

### Risk Mitigations

**Algorithm Reliability:**
- Optimisation continue pour éviter suggestions systématiquement incorrectes
- Plan de fallback : si algorithmes CV échouent → utilisation IA (post-MVP)
- Approche progressive : algorithmes simples → IA si nécessaire pour efficacité

**Error Handling:**
- Détection automatique photo floue avec demande nouvelle photo
- Gestion élégante des puzzles endommagés ou atypiques
- Messages clairs sur limites et contraintes de reconnaissance

**Performance Monitoring:**
- Surveillance temps de réponse et utilisation ressources
- Alertes si performance dégrade au-dessus des seuils
- Optimisations basées sur patterns d'utilisation réels

## Innovation & Novel Patterns

### Detected Innovation Areas

**1. Hybrid Computer Vision Architecture**
- **Innovation:** Combining traditional CV (pixel/histogram) with future AI integration
- **Novelty:** Evolution naturelle plutôt que révolution risquée - approche modulaire progressive
- **Value:** Réduit les risques tout en préparant l'avenir

**2. Gesture Innovation Through Dual-Photo Interaction**
- **Innovation:** Double-photo system (current puzzle state + individual piece)
- **Novelty:** Interaction gestuelle innovante que personne ne fait dans les apps de puzzles
- **Value:** Crée une expérience utilisateur unique et plus précise

**3. Confidence-Adaptive Suggestions**
- **Innovation:** System adjusts suggestion precision based on confidence level
- **Novelty:** Préservation autonomie utilisateur + aide intelligente selon certitude
- **Value:** Maintien le plaisir du puzzle tout en éliminant la frustration

**4. Gamification Through Execution Validation**
- **Innovation:** Automatic puzzle completion detection based on time interval between first and last photo
- **Novelty:** Validation passive sans input utilisateur requis
- **Value:** Métriques temporelles objectives pour classements et défis futurs

**5. Philosophy-Preserving Product Design**
- **Innovation:** "Aide à la décision" plutôt que solution automatique
- **Novelty:** Maintien autonomie et plaisir du puzzle tout en fournissant assistance
- **Value:** Différenciation fondamentale des solutions "trop automatiques"

### Market Context & Competitive Landscape

**Current Market Gap:**
- Existing puzzle apps focus on automatic solving or simple hints
- No solution combines CV assistance with user autonomy preservation
- Gamification limited to manual progress tracking
- No passive execution validation systems

**PuzzleHelper Differentiation:**
- Only solution maintaining puzzle-solving pleasure
- Hybrid technical approach allowing progressive enhancement
- Innovative gesture-based interaction model
- Automatic completion detection enabling future social features

### Validation Approach

**Technical Validation:**
- Unit testing with reference puzzles for algorithm accuracy
- Reproducibility testing: same photo + same piece = same suggestion
- Performance benchmarking: <10 seconds analysis time on Android
- Confidence scoring: probability metrics on 100-point scale

**User Experience Validation:**
- "Aha moment" testing: first-time user breakthrough scenarios
- Frustration reduction measurement: time-to-completion improvements
- Autonomy preservation assessment: user control vs assistance balance
- Gamification effectiveness: completion detection accuracy

### Risk Mitigation

**Innovation Risks:**
- **Complexity Management:** Hybrid architecture may increase development complexity
  - *Mitigation:* Modular design with clear MVP boundaries
- **User Adoption:** Novel interaction may require learning curve
  - *Mitigation:* Intuitive onboarding + progressive feature revelation
- **Technical Performance:** Dual-photo processing may impact mobile performance
  - *Mitigation:* Optimized algorithms + resource usage monitoring

**Fallback Strategies:**
- If hybrid CV fails → fallback to basic histogram comparison
- If gesture recognition fails → traditional single-photo upload
- If completion detection fails → manual completion confirmation
- If confidence scoring fails → binary suggestion system

## Mobile App Specific Requirements

### Project-Type Overview

**PuzzleHelper** est une application mobile native Android avec une approche progressive :
- **MVP :** Android uniquement (expertise développeur + ressources optimisées)
- **Post-MVP :** Extension iOS potentielle mais non prioritaire
- **Architecture :** Traitement local 100% offline avec mises à jour via Play Store

### Technical Architecture Considerations

**Platform Requirements:**
- **Target Platform :** Android natif pour MVP (expertise développeur)
- **Future Expansion :** iOS possible pour features mais "vraiment rien de sur" pour l'instant
- **Size Target :** <50MB pour rester cohérent et accessible
- **Performance :** Traitement local sur téléphone, pas de calcul serveur

**Device Permissions:**
- **Camera Access :** Essentiel pour reconnaissance pièces
- **Internal Storage :** Stockage interne app (PLUS COMPLIANT que SD card)
- **Privacy Compliance :** Permissions minimales, traitement local des données
- **No External Storage :** Évite permission écriture stockage externe

**Offline Mode:**
- **100% Offline :** Algorithmes CV fonctionnent entièrement localement
- **Update Strategy :** Mises à jour via Play Store (pas de serveur)
- **Data Local :** Photos, analyses et historique stockés localement
- **No Server Dependency :** Aucune connexion requise pour fonctionnement core

**Push Strategy:**
- **Smart Notifications :** Système de relance si inactivité prolongée ("signifie que t'avances pas sur le puzzle")
- **Progress Reminders :** Rappels de progression pour engagement
- **Community Features :** Notifications défis et classements (post-MVP)
- **Non-Intrusive :** Approche "pas trop intrusif" pour respecter expérience utilisateur

**Store Compliance:**
- **Play Store Guidelines :** Conformité complète aux exigences Android
- **Privacy Policy :** Traitement local des données = confidentialité renforcée
- **Permission Transparency :** Explication claire des permissions requises
- **Content Rating :** Classification appropriée pour application puzzle/famille

### Implementation Considerations

**Development Strategy:**
- **Native Android :** Optimisation performance et accès matériel natif
- **Modular Architecture :** Préparation pour future expansion iOS si nécessaire
- **Resource Optimization :** Algorithmes efficaces pour respecter limite <50MB
- **Testing Strategy :** Tests sur multiples appareils Android pour compatibilité

**Storage Management:**
- **Internal App Storage :** Photos, analyses, historique, logs stockés localement
- **Enhanced Compliance :** Stockage interne plus sécurisé et conforme que SD card
- **Data Portability :** Export possible des données utilisateur
- **Privacy by Design :** Traitement local = pas de transmission données sensibles

## Project Scoping & Phased Development

### MVP Strategy & Philosophy

**MVP Approach:** Problem-solving + Experience MVP
- **Problem-solving :** Résoudre immédiatement le problème de blocage sur pièces difficiles
- **Experience :** Créer le moment "aha!" le plus vite possible
- **Resource Requirements:** 1 développeur Android (votre expertise) + temps dédié

### MVP Feature Set (Phase 1)

**Core User Journeys Supported:**
- **Marc (passionné) :** Déblocage immédiat sur pièces difficiles avec moment "aha!"
- **Lilyan (créatrice) :** Test et validation des algorithmes de reconnaissance de base
- **Support (gestion erreurs) :** Gestion basique des cas limites et fallbacks

**Must-Have Capabilities:**
1. **Scan boîte puzzle** - Prédécoupage intelligent en N pièces virtuelles
2. **Prise photo pièce** - Interface caméra simple et rapide
3. **Reconnaissance et suggestion** - Algorithmes CV (pixel/histogramme) avec score confiance
4. **100% offline** - Traitement local, aucune connexion requise
5. **Interface simple et rapide** - UX minimaliste, <10 secondes analyse

**Explicitement Exclus du MVP:**
- ❌ Détection zones complétées
- ❌ Gamification et classements
- ❌ Notifications push
- ❌ Mode communauté
- ❌ Support iOS

### Post-MVP Features

**Phase 2 (Post-MVP - Growth):**
- Détection automatique zones complétées
- Algorithmes IA pour précision accrue
- Notifications intelligentes de progression
- Interface améliorée avec historique

**Phase 3 (Expansion - Vision):**
- Plateforme communautaire avec gamification
- Support iOS + Web
- Mode collaboratif et défis
- Intégration réseaux sociaux

### Risk Mitigation Strategy

**Technical Risks:**
- **Algorithm Performance :** Si CV <70% précision → fallback suggestions de zone
- **Mobile Performance :** Si analyse >10s → optimisation algorithmes progressive

**Market Risks:**
- **Adoption :** MVP résout problème réel de blocage → validation rapide
- **Competition :** Approche unique "aide décision" vs résolution automatique

**Resource Risks:**
- **Team Size :** 1 développeur suffisant pour MVP (votre expertise Android)
- **Timeline :** MVP focalisé = time-to-market réduit

## Functional Requirements

### Puzzle Management

- FR1: User can scan puzzle box image to create new puzzle project
- FR2: User can view list of created puzzle projects
- FR3: User can select existing puzzle project to work on
- FR4: User can delete puzzle project from local storage
- FR5: System can automatically pre-cut scanned box image into virtual puzzle pieces

### Recognition Engine

- FR6: User can capture photo of individual puzzle piece using device camera
- FR7: System can analyze captured piece image using computer vision algorithms
- FR8: System can compare piece image against virtual puzzle piece database
- FR9: System can generate position suggestion with confidence score
- FR10: System can provide precise position suggestion when confidence is high
- FR11: System can provide general area suggestion when confidence is low
- FR12: System can test piece image in multiple rotations for best match

### User Interface

- FR13: User can access camera interface for piece capture
- FR14: User can view puzzle box reference image while working
- FR15: User can see position suggestion overlay on reference image
- FR16: User can dismiss suggestion and continue manual search
- FR17: User can view confidence score for each suggestion
- FR18: User can navigate between different puzzle projects
- FR19: User can access simple settings menu

### Data Storage

- FR20: System can store puzzle projects locally on device
- FR21: System can save piece analysis results for future reference
- FR22: System can maintain puzzle completion progress
- FR23: User can export puzzle data for backup purposes
- FR24: System can store application logs for debugging

### System Integration

- FR25: System can request camera permission from user
- FR26: System can access device storage for local data persistence
- FR27: System can operate without internet connection
- FR28: System can comply with Android storage and privacy requirements

### Error Handling

- FR29: System can detect blurry or low-quality piece photos
- FR30: System can request new photo when image quality is insufficient
- FR31: System can handle cases where piece cannot be matched
- FR32: System can provide fallback suggestions when primary algorithms fail
- FR33: System can display clear error messages for recognition failures
- FR34: System can log errors for debugging and improvement

## Non-Functional Requirements

### Performance

**Response Time Requirements:**
- Position calculation time: <5 seconds for analysis and position suggestion
- Photo capture time: <2 seconds for capture and processing
- Overall analysis time: <10 seconds total (photo + analysis + suggestion)
- App startup time: <3 seconds for application launch

**Performance Impact:**
- Response time critical for user frustration prevention
- Performance directly linked to "aha!" moment creation
- Responsive interface maintains user engagement

### Accessibility

**MVP Positioning:** Non-prioritized for initial launch, but quality UX/UI required
**Target Users:** Standard users without specialized accessibility needs
**Basic Requirements:**
- Intuitive interface with logical navigation without complex learning
- Clean design with clear and readable interface
- Visual feedback for system responses (success, error, progress)
- Responsive design adapting to different Android screen sizes

**Post-MVP Considerations:** Evaluate accessibility improvements based on user demand

### Reliability

**Error Handling Requirements:**
- No recognition result: Offer photo retake or piece change options
- Low confidence score: Suggest new photo or manual continuation
- Algorithm failure: Fallback to general area suggestions
- Error recovery: Clear messages and recovery options

**Reliability Metrics:**
- Recognition accuracy: >70% correct suggestions
- Error recovery success: >90% users can continue after error
- System stability: <5% crashes during normal usage

**User Experience Impact:**
- Graceful degradation when algorithms fail
- Clear error messaging prevents user abandonment
- Multiple fallback options ensure task completion
