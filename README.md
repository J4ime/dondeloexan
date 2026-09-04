<p align="center">
  <img src="https://img.shields.io/badge/version-2.7.58-EC407A?style=for-the-badge&labelColor=1a1a2e" alt="Version"/>
  <img src="https://img.shields.io/badge/platform-Android-66BB6A?style=for-the-badge&labelColor=1a1a2e&logo=android" alt="Platform"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&labelColor=1a1a2e&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/DDD_Clean_Arch-FF6F00?style=for-the-badge&labelColor=1a1a2e" alt="Architecture"/>
  <img src="https://img.shields.io/badge/Room_SQLite-003B57?style=for-the-badge&labelColor=1a1a2e&logo=sqlite&logoColor=white" alt="Room"/>
  <img src="https://img.shields.io/badge/Ktor_OkHttp-009688?style=for-the-badge&labelColor=1a1a2e&logo=ktor&logoColor=white" alt="Ktor"/>
  <img src="https://img.shields.io/badge/Supabase-3ECF8E?style=for-the-badge&labelColor=1a1a2e&logo=supabase&logoColor=white" alt="Supabase"/>
</p>

<h1 align="center">
  🎬 ¿Dónde lo Echan?
</h1>
<p align="center">
  <em>Saber lo que ves. Controlar lo que te queda.</em>
</p>

<p align="center">
  Aplicación Android para que los amantes del cine y las series lleven un control absoluto de lo que ven. Centraliza catálogos de streaming, ofrece sugerencias basadas en disponibilidad real, gestiona listas de pendientes, automatiza la agenda de próximos lanzamientos y sincroniza tu biblioteca con la nube.
</p>

---

## ✨ Características Estrella

| | Funcionalidad | Detalle |
|---|---|---|
| ✅ | **Descubrimiento infinito** | Scroll infinito con carga diferida de contenido popular, filtrado por plataformas de streaming activas. Incluye la opción **Cines** para películas en cartelera. |
| 🔍 | **Búsqueda limpia** | Búsqueda sin filtros: sin nota mínima, sin filtro de plataformas ni de elementos ya guardados. Muestra **todo** lo que devuelve TMDB para el texto (incluye estrenos que aún no tienen nota). |
| 📺 | **Mis Series — 4 pestañas** | **Pendientes**, **En curso**, **Agenda** (al día, esperando próxima temporada) y **Terminadas**. Marcado en cascada con un solo clic; "En curso" se ordena por el último capítulo realmente visto. |
| 🎞️ | **Mis Películas** | Pendientes con rating IMDb visible. Tarjetas con fecha de estreno en cines, fin de cartelera y salto a streaming. |
| ☁️ | **Cuenta y sincronización** | Login (Supabase) y sincronización en la nube: catálogo global compartido + tablas por usuario. Refresco automático de la biblioteca cada 24 h con subida del catálogo a la nube (sesión anónima). |
| 🔔 | **Notificaciones** | Worker diario a las 08:00 (WorkManager) y aviso cuando una **fecha de estreno pendiente** aparece o se actualiza durante el refresco. |
| ⚙️ | **Filtro de disponibilidad** | `SUBSCRIPTION`, `RENT`, `BUY`, `FREE`, `ADS` — seleccionables en Ajustes. Todas activas por defecto. |
| 💀 | **Black List** | Icono de calavera. Oculta contenido de forma reactiva e instantánea en toda la app. |

---

## ⚙️ Configuración y Requisitos

### Entorno

- **Lenguaje:** Kotlin
- **SDK mínimo:** 26 (Android 8.0)
- **SDK objetivo:** 34 (Android 14)
- **Arquitectura:** DDD + Clean Architecture (capas `domain` / `data` / `presentation`)
- **Persistencia local:** Room (SQLite) con migraciones versionadas
- **Inyección de dependencias:** Koin
- **Guardarraíl de arquitectura:** task Gradle `verifyArchitecture` en `preBuild` que **falla el build** si `domain` importa `data` o si `presentation` toca `data.local.*`

### API Keys

La app consume varias APIs externas y el proyecto de Supabase. Configúralas en el archivo `local.properties` o como variables de entorno de compilación:

```properties
# ── TMDB (metadatos, watch providers e imágenes) ──
TMDB_ACCESS_TOKEN=<tu_token_tmdb>

# ── OMDb (ratings IMDb/Rotten Tomatoes) ──
OMDB_API_KEY=<tu_key_omdb>

# ── Supabase (catálogo en la nube y cuentas) ──
SUPABASE_URL=<https://tu-proyecto.supabase.co>
SUPABASE_ANON_KEY=<tu_anon_key_publishable>

# ── GitHub (comprobación de releases en Ajustes) ──
GITHUB_OWNER=J4ime
GITHUB_REPO=dondoloexan
```

Los valores se exponen en tiempo de compilación mediante `BuildConfig` y se inyectan en los clientes HTTP desde `NetworkModuleKoin.kt`.

---

## 📡 Arquitectura de Red y Optimización

El cliente HTTP se construye sobre **Ktor** con motor **OkHttp**, configurado con estrictos controles de concurrencia para evitar cuellos de botella:

| Parámetro | Valor | Descripción |
|---|---|---|
| **Dispatcher maxRequests** | `30` | Máximo de peticiones concurrentes globales |
| **Dispatcher maxRequestsPerHost** | `15` | Máximo por host (TMDB, Balloonerismm) |
| **ConnectionPool** | `10` conexiones, 30s keep‑alive | Reutiliza conexiones TCP, evita handshakes repetidos |
| **requestTimeout** | `15 000 ms` | Tiempo máximo para recibir respuesta completa |
| **connectTimeout** | `5 000 ms` | Límite de establecimiento de conexión TCP |
| **socketTimeout** | `5 000 ms` | Timeout de lectura entre paquetes |
| **retryOnConnectionFailure** | `true` | Reintento automático ante fallos de red |

> **OMDb** usa un pool independiente (`ConnectionPool(0, 1s)`) por ser un endpoint `GET` puro sin estado, con timeout de petición de `10s`.
>
> **Supabase** (REST/PostgREST + Auth) requiere una sesión autenticada para las tablas de usuario; el **catálogo global** se escribe con la **sesión anónima**, por lo que puede actualizarse sin que el usuario inicie sesión.

Este diseño evita que una ralentización en TMDB bloquee las peticiones a OMDb o Balloonerismm, y previene el agotamiento del pool de conexiones del dispositivo.

---

## 🧭 Flujo de datos

- **Búsqueda / Descubrir:** TMDB (`/search/multi`, trending, watch providers) · OMDb (ratings) · Balloonerismm/IMDb (worker) · Wikidata (sagas/precuelas) · Filmaffinity (nota y críticas en español).
- **Local:** Room (`movies`, `tv_shows`, `tv_show_progress`, `blacklist`, `search_history`, `user_platforms`, `critic_reviews`, `fa_movie_data`) + DataStore (preferencias de disponibilidad, timestamp del último refresco, sesión).
- **Nube (Supabase):** catálogo global (compartido, sesión anónima) + tablas de usuario (`user_movies`, `user_tv_shows`, `tv_show_progress`, `search_history`, `user_platforms`, `blacklist`) con RLS.

---

## 🛠️ Instalación y Setup

```bash
# 1. Clonar el repositorio
git clone https://github.com/J4ime/dondeloexan.git
cd dondeloexan

# 2. Configurar API keys en local.properties
echo "TMDB_ACCESS_TOKEN=tu_token_aqui" >> local.properties
echo "OMDB_API_KEY=tu_key_aqui" >> local.properties
echo "SUPABASE_URL=https://tu-proyecto.supabase.co" >> local.properties
echo "SUPABASE_ANON_KEY=tu_anon_key_aqui" >> local.properties

# 3. Compilar y ejecutar en modo debug
./gradlew assembleDebug

# El APK se genera en:
# app/build/outputs/apk/debug/DondLoExan.<version>-debug.apk
```

También puedes descargar la última `release` desde la página de [releases](https://github.com/J4ime/dondeloexan/releases) de GitHub.

---

## 📊 Modelo de Datos de Disponibilidad

Cada contenido puede tener múltiples formas de consumo. El siguiente enum —definido en `domain/model/Content.kt`— modela todos los casos:

```kotlin
/**
 * Define el tipo de disponibilidad comercial de una película o serie
 * en una plataforma de streaming concreta.
 *
 * Los usuarios pueden activar/desactivar cada tipo desde Ajustes.
 * Por defecto, todos están habilitados.
 */
enum class AvailabilityType {
    /** Suscripción activa (Netflix, Prime, Disney+…) */
    SUBSCRIPTION,
    /** Alquiler temporal (Apple TV, Rakuten…) */
    RENT,
    /** Compra digital definitiva */
    BUY,
    /** Contenido gratuito con publicidad (Pluto TV, Atresplayer…) */
    FREE,
    /** Publicidad como único coste (Tubi, Freevee…) */
    ADS
}
```

Este modelo alimenta el **filtro de plataformas** de la sección Descubrir y la pantalla de **Ajustes de disponibilidad**, donde el usuario decide qué tipos de acceso quiere ver reflejados en los resultados.

---

## 🗂️ Estructura del proyecto

- `domain/` — modelos de dominio y contratos de repositorio (puros: no importan `data`).
- `data/` — implementaciones de repositorios, DAOs/entidades Room, clientes HTTP (TMDB/OMDb/Supabase/Wikidata/Filmaffinity), catálogo en la nube y servicios de sync/sesión.
- `presentation/` — ViewModels + Compose UI (sobre modelos de dominio).
- `di/` — módulos Koin.
- `worker/` — tareas en segundo plano (WorkManager).

---

<p align="center">
  <sub>Hecho con ❤️ y mucho café · ¿Dónde lo Echan? v2.7.58</sub>
</p>
