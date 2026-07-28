# Fix: Critic reviews de EL PAÍS no aparecen

## Problema
Las críticas de prensa de EL PAÍS no se muestran en el detalle de la app.

## Causa raíz
1. **Selector CSS incorrecto** (`FilmaffinityScraper.kt:89`): `td.author em a, td.author strong a` requiere un `<a>` dentro de `<em>`/`<strong>`. Si la publicación está en `<em>EL PAÍS</em>` sin enlace, el selector no la encuentra.
2. **Sin priorización**: El límite de 5 críticas total puede excluir a EL PAÍS si hay 5+ reseñas españolas antes en el HTML.

## Cambios

### 1. Selector CSS más robusto (línea 89)
```kotlin
// ANTES:
val publicationEl = row.selectFirst("td.author em a, td.author strong a")
// DESPUÉS:
val publicationEl = row.selectFirst("td.author em a, td.author strong a, td.author em, td.author strong")
```

### 2. Priorizar EL PAÍS (líneas 116-119)
```kotlin
// ANTES:
val fromSpanish = reviews.filter { it.publication.normalize() in SPANISH_MEDIA }
val nonSpanish = reviews.filter { it.publication.normalize() !in SPANISH_MEDIA }
fromSpanish.take(5) + nonSpanish.take((5 - fromSpanish.size).coerceAtLeast(0))

// DESPUÉS:
val PRIORITY_MEDIA = setOf("el pais")
val fromSpanish = reviews.filter { it.publication.normalize() in SPANISH_MEDIA }
val nonSpanish = reviews.filter { it.publication.normalize() !in SPANISH_MEDIA }
val (priority, rest) = fromSpanish.partition { it.publication.normalize() in PRIORITY_MEDIA }
(priority + rest).take(5) + nonSpanish.take((5 - fromSpanish.size).coerceAtLeast(0))
```

### 3. Añadir constante PRIORITY_MEDIA en companion object
```kotlin
private val PRIORITY_MEDIA = setOf("el pais")
```
