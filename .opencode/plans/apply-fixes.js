const fs = require('fs');
const path = require('path');

const base = 'D:\\Code\\Repos\\DondLoExan';

// ===== 1. FilmaffinityScraper.kt =====
let scraper = fs.readFileSync(path.join(base, 'app/src/main/java/com/dondeloexan/data/remote/filmaffinity/FilmaffinityScraper.kt'), 'utf8');

scraper = scraper.replace(
  `val publicationEl = row.selectFirst("td.author em a, td.author strong a, td.author em, td.author strong")`,
  `val publicationEl = row.selectFirst("td.author em a, td.author strong a, td.author em, td.author strong")\n                    val publicationOwnText = row.selectFirst("td.author")?.ownText()?.trim()`
);

scraper = scraper.replace(
  `val publication = publicationEl?.text()?.trim() ?: ""`,
  `val publication = publicationEl?.text()?.trim()\n                        ?: publicationOwnText?.takeIf { it.isNotBlank() }\n                        ?: ""`
);

scraper = scraper.replace(
  `lowercase()\n            .replace('á', 'a').replace('é', 'e').replace('í', 'i')`,
  `lowercase()\n            .replace('\\u00a0', ' ')\n            .replace('á', 'a').replace('é', 'e').replace('í', 'i')`
);

fs.writeFileSync(path.join(base, 'app/src/main/java/com/dondeloexan/data/remote/filmaffinity/FilmaffinityScraper.kt'), scraper);
console.log('1. FilmaffinityScraper.kt - OK');

// ===== 2. LibraryItemCard.kt =====
let library = fs.readFileSync(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/library/LibraryItemCard.kt'), 'utf8');

// Remove MovieOverlayBadge call block
const badgeCallRegex = /        if \(releaseDate != null && totalEpisodes == null\) \{\n            MovieOverlayBadge\(\n                releaseDate = releaseDate,\n                platforms = streamingPlatforms,\n                modifier = Modifier\n                    \.align\(Alignment\.TopStart\)\n                    \.padding\(6\.dp\)\n            \)\n        \}\n/;
library = library.replace(badgeCallRegex, '');

// Remove CinemaInfo + MovieOverlayBadge data class + composable
const cinemaInfoRegex = /^private data class CinemaInfo\([\s\S]*?^\}\n\nprivate fun MovieOverlayBadge\([\s\S]*?^    \}\n\}/m;
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

fs.writeFileSync(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/library/LibraryItemCard.kt'), library);
console.log('2. LibraryItemCard.kt - OK');

// ===== 3. SearchItemCard.kt =====
let search = fs.readFileSync(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/discover/components/SearchItemCard.kt'), 'utf8');

// Remove cinema label block
const cinemaBlockRegex = /            if \(content\.type\.name == "MOVIE" && !content\.releaseDate\.isNullOrBlank\(\)\) \{\n                val nonCinemaPlatforms = content\.streamingPlatforms\.none \{ it\.platformName != "Cine" \}\n[\s\S]*?                \}\n            \}\n\n/;
search = search.replace(cinemaBlockRegex, '');

// Update PlatformLogoRow to show 🎬 for Cine
const logoRowRegex = /            \} else \{\n                Surface\(\n                    shape = RoundedCornerShape\(4\.dp\),\n                    color = Color\.White\.copy\(alpha = 0\.15f\)\n                \) \{\n                    Text\(\n                        text = platform\.platformName\.take\(2\),\n                        modifier = Modifier\.padding\(horizontal = 4\.dp, vertical = 2\.dp\),\n                        style = UbuntuTypography\.labelSmall,\n                        color = TextSecondary,\n                        fontSize = 9\.sp\n                    \)\n                \}\n            \}/;
search = search.replace(logoRowRegex,
  `            } else if (platform.platformName == "Cine") {
                Text("\uD83C\uDFAC", fontSize = 16.sp)
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = platform.platformName.take(2),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = UbuntuTypography.labelSmall,
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }`
);

fs.writeFileSync(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/discover/components/SearchItemCard.kt'), search);
console.log('3. SearchItemCard.kt - OK');

// ===== 4. MediaDetailScreen.kt =====
let detail = fs.readFileSync(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/detail/MediaDetailScreen.kt'), 'utf8');

// Replace appendCinemaPlatform function
const oldAppendRegex = /private fun appendCinemaPlatform\([\s\S]*?^    \}\n\}/m;
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

// Replace StreamingSection signature + cinemaPlatforms filter
detail = detail.replace(
  `private fun StreamingSection(platforms: List<StreamingAvailability>, futureReleaseLabel: String? = null, futurePlatformInfo: List<String>? = null) {\n    val cinemaPlatforms = platforms.filter { it.platformName.contains("Cine", ignoreCase = true) || it.platformName.contains("Estreno", ignoreCase = true) }`,
  `private fun StreamingSection(platforms: List<StreamingAvailability>, futureReleaseLabel: String? = null, futurePlatformInfo: List<String>? = null, cinemaReleaseDate: String? = null) {\n    val cinemaPlatforms = platforms.filter { it.platformName == "Cine" }`
);

// Replace platformRow("Cine", cinemaPlatforms) call
detail = detail.replace(
  `        platformRow("Cine", cinemaPlatforms)`,
  `        if (cinemaPlatforms.isNotEmpty()) {\n            val cinemaPlatform = cinemaPlatforms.first()\n            PlatformCard(cinemaPlatform, releaseDate = cinemaReleaseDate)\n        }`
);

// Replace PlatformCard function
const oldCardRegex = /private fun PlatformCard\(platform: StreamingAvailability\) \{[\s\S]*?^    \}\n\}/m;
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

// Update StreamingSection call in FichaTab to pass releaseDate
detail = detail.replace(
  `item { StreamingSection(displayPlatforms, futureReleaseLabel, futurePlatformInfo) }`,
  `item { StreamingSection(displayPlatforms, futureReleaseLabel, futurePlatformInfo, content.releaseDate) }`
);

fs.writeFileSync(path.join(base, 'app/src/main/java/com/dondeloexan/presentation/detail/MediaDetailScreen.kt'), detail);
console.log('4. MediaDetailScreen.kt - OK');

// ===== 5. build.gradle.kts =====
let gradle = fs.readFileSync(path.join(base, 'app/build.gradle.kts'), 'utf8');
gradle = gradle.replace('versionCode = 93', 'versionCode = 95');
gradle = gradle.replace('versionName = "2.7.11"', 'versionName = "2.7.13"');
fs.writeFileSync(path.join(base, 'app/build.gradle.kts'), gradle);
console.log('5. build.gradle.kts - OK');

console.log('\nAll files updated successfully!');
