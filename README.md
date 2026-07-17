<p align="center">
  <img src="https://img.shields.io/badge/version-2.3.9-EC407A?style=for-the-badge&labelColor=1a1a2e" alt="Version"/>
  <img src="https://img.shields.io/badge/platform-Android-66BB6A?style=for-the-badge&labelColor=1a1a2e&logo=android" alt="Platform"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&labelColor=1a1a2e&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/MVVM_Clean_Arch-FF6F00?style=for-the-badge&labelColor=1a1a2e" alt="Architecture"/>
  <img src="https://img.shields.io/badge/Room_SQLite-003B57?style=for-the-badge&labelColor=1a1a2e&logo=sqlite&logoColor=white" alt="Room"/>
  <img src="https://img.shields.io/badge/Ktor_OkHttp-009688?style=for-the-badge&labelColor=1a1a2e&logo=ktor&logoColor=white" alt="Ktor"/>
</p>

<h1 align="center">
  🎬 ¿Dónde lo Echan?
</h1>
<p align="center">
  <em>Saber lo que ves. Controlar lo que te queda.</em>
</p>

<p align="center">
  Aplicación Android para que los amantes del cine y las series lleven un control absoluto de lo que ven. Centraliza catálogos de streaming, ofrece sugerencias basadas en disponibilidad real, gestiona listas de pendientes y automatiza la agenda de próximos lanzamientos.
</p>

---

## ✨ Características Estrella

| | Funcionalidad | Detalle |
|---|---|---|
| ✅ | **Descubrimiento infinito** | Scroll infinito con carga diferida de contenido popular, filtrado por plataformas de streaming activas. Incluye la opción **Cines** para películas en cartelera. |
| 🔍 | **Búsqueda Pura** | Búsqueda manual sin filtro de plataforma para resultados globales. Solo la Black List es inquebrantable. |
| 📺 | **Mis Series — 3 pestañas** | **Seguimiento** (series activas), **Agenda** (100% visto, esperando nueva temporada), **Terminadas** (finalizadas). Marcado en cascada con un solo clic. |
| 🎞️ | **Mis Películas** | Pendientes con rating IMDb visible. Tarjetas con fecha de estreno en cines, fin de cartelera y salto a streaming. |
| ⚙️ | **Filtro de disponibilidad** | `SUBSCRIPTION`, `RENT`, `BUY`, `FREE`, `ADS` — seleccionables en Ajustes. Todas activas por defecto. |
| 💀 | **Black List** | Icono de calavera. Oculta contenido de forma reactiva e instantánea en toda la app con animaciones fluidas. |
| 🔔 | **Notificaciones diarias** | Worker a las 08:00 AM (WorkManager). Actualiza fechas y episodios en segundo plano. Notifica los estrenos del día. |

---

## ⚙️ Configuración y Requisitos

### Entorno

- **Lenguaje:** Kotlin
- **SDK mínimo:** 26 (Android 8.0)
- **SDK objetivo:** 34 (Android 14)
- **Arquitectura:** MVVM + Clean Architecture
- **Persistencia local:** Room (SQLite) con migraciones versionadas
- **Inyección de dependencias:** Koin

### API Keys

La app consume dos APIs externas. Configúralas en el archivo `local.properties` o como variables de entorno de compilación:

```properties
# ── TMDB (metadatos, watch providers e imágenes) ──
TMDB_ACCESS_TOKEN=eyJhbGciOiJIUzI1NiJ9...

# ── OMDb (búsqueda principal, ratings IMDb/Rotten Tomatoes) ──
OMDB_API_KEY=trilogy
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

> **OMDb** usa un pool independiente (`ConnectionPool(0, 1s)`) por ser un endpoint `GET` puro sin estado que no requiere reutilización de conexiones, y su timeout de petición es de `10s`.

Este diseño evita que una ralentíz en TMDB bloquee las peticiones a OMDb o Balloonerismm, y previene el agotamiento del pool de conexiones del dispositivo.

---

## 🛠️ Instalación y Setup

```bash
# 1. Clonar el repositorio
git clone https://github.com/J4ime/dondeloexan.git
cd dondeloexan

# 2. Configurar API keys en local.properties
echo "TMDB_ACCESS_TOKEN=tu_token_aqui" >> local.properties
echo "OMDB_API_KEY=tu_key_aqui" >> local.properties

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

<p align="center">
  <sub>Hecho con ❤️ y mucho café · ¿Dónde lo Echan? v2.3.9</sub>
</p>
