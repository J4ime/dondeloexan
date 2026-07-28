const fs = require('fs');
const path = require('path');

const base = 'D:\\Code\\Repos\\DondLoExan';

function readFile(p) {
  return fs.readFileSync(p, 'utf8').replace(/\r\n/g, '\n');
}
function writeFile(p, content) {
  fs.writeFileSync(p, content, 'utf8');
}

// ===== 2. LibraryItemCard.kt =====
let library = readFile(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/library/LibraryItemCard.kt'));

// Remove MovieOverlayBadge call block
const badgeCallRegex = /        if \(releaseDate != null && totalEpisodes == null\) \{\n            MovieOverlayBadge\(\n                releaseDate = releaseDate,\n                platforms = streamingPlatforms,\n                modifier = Modifier\n                    \.align\(Alignment\.TopStart\)\n                    \.padding\(6\.dp\)\n            \)\n        \}\n/;
library = library.replace(badgeCallRegex, '');

// Remove CinemaInfo data class + MovieOverlayBadge composable
const cinemaInfoRegex = /^private data class CinemaInfo\([\s\S]*?^    \}\n\}$\n\n^private fun MovieOverlayBadge\([\s\S]*?^    \}\n\}$/m;
library = library.replace(cinemaInfoRegex, '');

// Update PlatformBadgeRow to show 🎬 for Cine
const badgeRowRegex = /            \} else \{\n                Surface\(\n                    shape = RoundedCornerShape\(3\.dp\),\n                    color = Color\.White\.copy\(alpha = 0\.15f\)\n                \) \{\n                    Text\(\n                        text = platform\.platformName\.take\(2\),\n                        modifier = Modifier\.padding\(horizontal = 3\.dp, vertical = 1\.dp\),\n                        style = UbuntuTypography\.labelSmall,\n                        color = TextSecondary,\n                        fontSize = 8\.sp\n                    \)\n                \}\n            \}/;
library = library.replace(badgeRowRegex,
  `            } else if (platform.platformName == "Cine") {
                Text("\uD83C\uDFAC", fontSize = 14.sp)
            } else {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = platform.platformName.take(2),
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                        style = UbuntuTypography.labelSmall,
                        color = TextSecondary,
                        fontSize = 8.sp
                    )
                }
            }`
);

writeFile(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/library/LibraryItemCard.kt'), library);
console.log('2. LibraryItemCard.kt - OK');

// ===== 4. MediaDetailScreen.kt =====
let detail = readFile(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/detail/MediaDetailScreen.kt'));

// Replace appendCinemaPlatform function
const oldAppendRegex = /^private fun appendCinemaPlatform\([\s\S]*?^    \}\n\}$/m;
const newAppend = `private fun appendCinemaPlatform(
    releaseDate: String,
    platforms: List<StreamingAvailability>
): List<StreamingAvailability> {
    try {
        val date = LocalDate.parse(releaseDate)
        val now = LocalDate.now()
        val daysSinceRelease = ChronoUnit.DAYS.between(date, now)
        val daysUntilRelease = ChronoUnit.DAYS.between(now, date)
        val hasSubscription = platforms.any { it.availabilityType == AvailabilityType.SUBSCRIPTION }
        val isInCinemas = daysSinceRelease in 0..90 && !hasSubscription
        val isFuture = daysUntilRelease > 0 && !hasSubscription

        if (isInCinemas || isFuture) {
            val cinemaPlatform = StreamingAvailability(
                platformName = "Cine",
                platformId = null,
                logoUrl = null,
                availabilityType = AvailabilityType.ADS
            )
            return listOf(cinemaPlatform) + platforms
        }
    } catch (e: Exception) {
        AppLogger.e("MediaDetailScreen", "cinemaPlatform: $releaseDate", e)
    }
    return platforms
}`;
detail = detail.replace(oldAppendRegex, newAppend);

// Replace PlatformCard function
const oldCardRegex = /^private fun PlatformCard\(platform: StreamingAvailability\) \{[\s\S]*?^    \}\n\}$/m;
const newCard = `private fun PlatformCard(platform: StreamingAvailability, releaseDate: String? = null) {
    Column(
        modifier = Modifier.width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (platform.platformName == "Cine") {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83C\uDFAC", fontSize = 20.sp)
            }
        } else if (platform.logoUrl != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(platform.logoUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = platform.platformName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    platform.platformName.take(2),
                    style = UbuntuTypography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        Text(
            if (platform.platformName == "Cine" && releaseDate != null) releaseDate else platform.platformName,
            style = UbuntuTypography.labelSmall,
            color = if (platform.platformName == "Cine") EleganteRose else TextSecondary,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}`;
detail = detail.replace(oldCardRegex, newCard);

writeFile(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/detail/MediaDetailScreen.kt'), detail);
console.log('4. MediaDetailScreen.kt - OK');

// ===== 5. build.gradle.kts =====
let gradle = readFile(path.join(base, 'app/build.gradle.kts'));
// v2.7.12 committed versionCode=94, versionName=2.7.12
gradle = gradle.replace('versionCode = 94', 'versionCode = 95');
gradle = gradle.replace('versionName = "2.7.12"', 'versionName = "2.7.13"');
writeFile(path.join(base, 'app/build.gradle.kts'), gradle);
console.log('5. build.gradle.kts - OK (v2.7.13)');

console.log('\nLibraryItemCard, MediaDetailScreen, build.gradle.kts updated!');
