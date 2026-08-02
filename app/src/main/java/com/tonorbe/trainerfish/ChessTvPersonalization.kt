package com.tonorbe.trainerfish

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal const val MAX_CHESS_TV_FAVORITES = 5

internal data class ChessTvCountryOption(
    val id: String,
    val name: String,
    val federationCodes: Set<String>,
    val flag: String
) {
    val label: String
        get() = listOf(flag, name).filter { it.isNotBlank() }.joinToString(" ")
}

internal data class ChessTvFavoritePlayer(
    val key: String,
    val displayName: String,
    val fideId: Long? = null,
    val lichessUsername: String? = null,
    val aliases: List<String> = emptyList()
)

internal data class ChessTvFollowProfile(
    val setupComplete: Boolean = false,
    val countryId: String? = null,
    val favorites: List<ChessTvFavoritePlayer> = emptyList()
) {
    val country: ChessTvCountryOption?
        get() = ChessTvCountries.byId(countryId)
}

internal object ChessTvFavoritePlayers {
    val suggestions = listOf(
        ChessTvFavoritePlayer("fide:1503014", "Magnus Carlsen", 1503014, aliases = listOf("Carlsen, Magnus")),
        ChessTvFavoritePlayer("fide:2016192", "Hikaru Nakamura", 2016192, aliases = listOf("Nakamura, Hikaru")),
        ChessTvFavoritePlayer("fide:5202213", "Wesley So", 5202213, aliases = listOf("So, Wesley")),
        ChessTvFavoritePlayer("fide:35009192", "Arjun Erigaisi", 35009192, aliases = listOf("Erigaisi Arjun", "Erigaisi, Arjun")),
        ChessTvFavoritePlayer(
            "fide:25059530",
            "R Praggnanandhaa",
            25059530,
            aliases = listOf("Praggnanandhaa R", "Praggnanandhaa, Rameshbabu")
        ),
        ChessTvFavoritePlayer("fide:12940690", "Vincent Keymer", 12940690, aliases = listOf("Keymer, Vincent")),
        ChessTvFavoritePlayer("fide:46616543", "Gukesh D", 46616543, aliases = listOf("Gukesh Dommaraju", "Gukesh, D")),
        ChessTvFavoritePlayer("fide:2020009", "Fabiano Caruana", 2020009, aliases = listOf("Caruana, Fabiano")),
        ChessTvFavoritePlayer(
            "fide:14204118",
            "Nodirbek Abdusattorov",
            14204118,
            aliases = listOf("Abdusattorov, Nodirbek")
        ),
        ChessTvFavoritePlayer("fide:12573981", "Alireza Firouzja", 12573981, aliases = listOf("Firouzja, Alireza")),
        ChessTvFavoritePlayer("fide:4168119", "Ian Nepomniachtchi", 4168119, aliases = listOf("Nepomniachtchi, Ian")),
        ChessTvFavoritePlayer("fide:8603677", "Ding Liren", 8603677, aliases = listOf("Ding, Liren")),
        ChessTvFavoritePlayer("fide:8603405", "Wei Yi", 8603405, aliases = listOf("Wei, Yi")),
        ChessTvFavoritePlayer("fide:24116068", "Anish Giri", 24116068, aliases = listOf("Giri, Anish")),
        ChessTvFavoritePlayer("fide:13300474", "Levon Aronian", 13300474, aliases = listOf("Aronian, Levon")),
        ChessTvFavoritePlayer("fide:8603006", "Ju Wenjun", 8603006, aliases = listOf("Ju, Wenjun")),
        ChessTvFavoritePlayer("fide:8605114", "Lei Tingjie", 8605114, aliases = listOf("Lei, Tingjie")),
        ChessTvFavoritePlayer("fide:8602980", "Hou Yifan", 8602980, aliases = listOf("Hou, Yifan"))
    )

    fun customLichess(username: String): ChessTvFavoritePlayer {
        val clean = username.trim().removePrefix("@").trim()
        return ChessTvFavoritePlayer(
            key = "lichess:${clean.lowercase(Locale.ROOT)}",
            displayName = "@$clean",
            lichessUsername = clean,
            aliases = listOf(clean)
        )
    }
}

internal object ChessTvCountries {
    private val fideCodeOverrides = mapOf(
        "DZ" to "ALG", "AO" to "ANG", "AG" to "ANT", "BS" to "BAH", "BD" to "BAN",
        "BB" to "BAR", "BZ" to "BIZ", "BM" to "BER", "BT" to "BHU", "BW" to "BOT",
        "BN" to "BRU", "BG" to "BUL", "KH" to "CAM", "CL" to "CHI", "HR" to "CRO",
        "DK" to "DEN", "DO" to "DOM", "SV" to "ESA", "FO" to "FAI", "DE" to "GER",
        "GR" to "GRE", "GT" to "GUA", "HT" to "HAI", "HN" to "HON", "ID" to "INA",
        "IR" to "IRI", "KW" to "KUW", "LV" to "LAT", "MG" to "MAD", "MY" to "MAS",
        "MU" to "MRI", "MM" to "MYA", "NL" to "NED", "NI" to "NCA", "PS" to "PLE",
        "PY" to "PAR", "PH" to "PHI", "PT" to "POR", "PR" to "PUR", "SA" to "KSA",
        "SI" to "SLO", "ZA" to "RSA", "LK" to "SRI", "SD" to "SUD", "CH" to "SUI",
        "TW" to "TPE", "AE" to "UAE", "VE" to "VEN", "VN" to "VIE", "ZM" to "ZAM",
        "ZW" to "ZIM"
    )

    val all: List<ChessTvCountryOption> by lazy {
        val isoCountries = Locale.getISOCountries().mapNotNull { iso2 ->
            val locale = Locale.Builder().setRegion(iso2).build()
            val name = locale.getDisplayCountry(Locale.ENGLISH).trim()
            val iso3 = runCatching { locale.getISO3Country().uppercase(Locale.ROOT) }.getOrNull()
            if (name.isBlank() || iso3.isNullOrBlank()) return@mapNotNull null
            val fide = fideCodeOverrides[iso2]
            ChessTvCountryOption(
                id = iso2,
                name = name,
                federationCodes = listOfNotNull(iso3, fide).map { it.uppercase(Locale.ROOT) }.toSet(),
                flag = countryFlag(iso2)
            )
        }
        (isoCountries + listOf(
            ChessTvCountryOption("FIDE-ENG", "England", setOf("ENG"), "🏴"),
            ChessTvCountryOption("FIDE-SCO", "Scotland", setOf("SCO"), "🏴"),
            ChessTvCountryOption("FIDE-WLS", "Wales", setOf("WLS"), "🏴"),
            ChessTvCountryOption("FIDE-KOS", "Kosovo", setOf("KOS", "XKX"), "🇽🇰")
        )).distinctBy { it.id }.sortedBy { it.name }
    }

    fun byId(id: String?): ChessTvCountryOption? = all.firstOrNull { it.id == id }

    fun defaultCountryId(): String? {
        val iso2 = Locale.getDefault().country.uppercase(Locale.ROOT)
        return all.firstOrNull { it.id == iso2 }?.id
    }

    private fun countryFlag(iso2: String): String {
        if (iso2.length != 2) return ""
        val first = Character.toChars(0x1F1E6 + (iso2[0].uppercaseChar() - 'A'))
        val second = Character.toChars(0x1F1E6 + (iso2[1].uppercaseChar() - 'A'))
        return String(first) + String(second)
    }
}

internal class ChessTvFollowStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ChessTvFollowProfile {
        val favorites = runCatching {
            val array = JSONArray(preferences.getString(KEY_FAVORITES, "[]") ?: "[]")
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val key = item.optString("key").trim()
                    val displayName = item.optString("name").trim()
                    if (key.isBlank() || displayName.isBlank()) continue
                    val suggestion = ChessTvFavoritePlayers.suggestions.firstOrNull { it.key == key }
                    add(
                        suggestion ?: ChessTvFavoritePlayer(
                            key = key,
                            displayName = displayName,
                            fideId = item.optLongOrNull("fideId"),
                            lichessUsername = item.optString("lichessUsername").trim().takeIf { it.isNotBlank() },
                            aliases = listOf(displayName)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return ChessTvFollowProfile(
            setupComplete = preferences.getBoolean(KEY_SETUP_COMPLETE, false),
            countryId = preferences.getString(KEY_COUNTRY_ID, null),
            favorites = favorites.distinctBy { it.key }.take(MAX_CHESS_TV_FAVORITES)
        )
    }

    fun save(profile: ChessTvFollowProfile) {
        val favoritesJson = JSONArray().apply {
            profile.favorites.distinctBy { it.key }.take(MAX_CHESS_TV_FAVORITES).forEach { favorite ->
                put(JSONObject().apply {
                    put("key", favorite.key)
                    put("name", favorite.displayName)
                    favorite.fideId?.let { put("fideId", it) }
                    favorite.lichessUsername?.let { put("lichessUsername", it) }
                })
            }
        }
        preferences.edit()
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .putString(KEY_COUNTRY_ID, profile.countryId)
            .putString(KEY_FAVORITES, favoritesJson.toString())
            .apply()
    }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        takeIf { has(name) && !isNull(name) }?.optLong(name)

    private companion object {
        const val PREFERENCES_NAME = "chess_tv_following"
        const val KEY_SETUP_COMPLETE = "setup_complete"
        const val KEY_COUNTRY_ID = "country_id"
        const val KEY_FAVORITES = "favorite_players"
    }
}

internal fun LichessBroadcastSelection.matchesCountry(country: ChessTvCountryOption): Boolean =
    white.matchesCountry(country) || black.matchesCountry(country)

internal fun LichessBroadcastSelection.matchesFavorites(
    favorites: List<ChessTvFavoritePlayer>
): Boolean = favorites.any { favorite ->
    white.matchesFavorite(favorite) || black.matchesFavorite(favorite)
}

private fun LichessTvPlayer.matchesCountry(country: ChessTvCountryOption): Boolean {
    val code = federation?.trim()?.uppercase(Locale.ROOT) ?: return false
    return code in country.federationCodes
}

private fun LichessTvPlayer.matchesFavorite(favorite: ChessTvFavoritePlayer): Boolean {
    if (favorite.fideId != null && fideId == favorite.fideId) return true
    val favoriteUsername = favorite.lichessUsername?.let(::normalizedHandle)
    if (favoriteUsername != null) {
        if (lichessUsername?.let(::normalizedHandle) == favoriteUsername) return true
        if (normalizedHandle(name) == favoriteUsername) return true
    }
    val playerName = normalizedNameSignature(name)
    return (listOf(favorite.displayName) + favorite.aliases)
        .map(::normalizedNameSignature)
        .filter { it.isNotBlank() }
        .any { it == playerName }
}

private fun normalizedHandle(value: String): String =
    value.trim().removePrefix("@").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() || it == '_' || it == '-' }

private fun normalizedNameSignature(value: String): String {
    val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return decomposed
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\b(gm|im|fm|cm|wgm|wim|wfm|wcm)\\b"), " ")
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }
        .sorted()
        .joinToString(" ")
}

private enum class ChessTvSetupStep { COUNTRY, FAVORITES }

@Composable
internal fun ChessTvFollowSetupDialog(
    initialProfile: ChessTvFollowProfile,
    onDismiss: () -> Unit,
    onSave: (ChessTvFollowProfile) -> Unit
) {
    var step by remember(initialProfile) { mutableStateOf(ChessTvSetupStep.COUNTRY) }
    var selectedCountryId by remember(initialProfile) {
        mutableStateOf(
            if (initialProfile.setupComplete) initialProfile.countryId
            else initialProfile.countryId ?: ChessTvCountries.defaultCountryId()
        )
    }
    var selectedFavorites by remember(initialProfile) { mutableStateOf(initialProfile.favorites) }
    var countryQuery by remember { mutableStateOf("") }
    var customUsername by remember { mutableStateOf("") }
    val selectedCountry = ChessTvCountries.byId(selectedCountryId)
    val filteredCountries = remember(countryQuery) {
        val query = countryQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) ChessTvCountries.all
        else ChessTvCountries.all.filter {
            it.name.lowercase(Locale.ROOT).contains(query) ||
                it.federationCodes.any { code -> code.lowercase(Locale.ROOT).contains(query) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (step == ChessTvSetupStep.COUNTRY) "Follow your country" else "Choose favorite players",
                fontWeight = FontWeight.Black
            )
        },
        text = {
            if (step == ChessTvSetupStep.COUNTRY) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Would you like Chess TV to find live broadcast games featuring players from your country? Choose a country below.",
                        color = Color(0xFF4E3B2A),
                        fontSize = 13.sp
                    )
                    selectedCountry?.let {
                        Text(
                            "Selected: ${it.label}",
                            color = Color(0xFF166534),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedTextField(
                        value = countryQuery,
                        onValueChange = { countryQuery = it },
                        label = { Text("Search country") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 310.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        item {
                            ChessTvSetupChoiceRow(
                                title = "No country preference",
                                subtitle = "Show favorite players and tournaments only",
                                selected = selectedCountryId == null,
                                onClick = { selectedCountryId = null }
                            )
                        }
                        items(filteredCountries, key = { it.id }) { country ->
                            ChessTvSetupChoiceRow(
                                title = country.label,
                                subtitle = country.federationCodes.sorted().joinToString(" / "),
                                selected = selectedCountryId == country.id,
                                onClick = { selectedCountryId = country.id }
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose up to $MAX_CHESS_TV_FAVORITES players. Chess TV will put any live broadcast boards featuring them above the tournament list.",
                        color = Color(0xFF4E3B2A),
                        fontSize = 13.sp
                    )
                    Text(
                        "${selectedFavorites.size}/$MAX_CHESS_TV_FAVORITES selected",
                        color = Color(0xFF166534),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customUsername,
                            onValueChange = { customUsername = it },
                            label = { Text("Lichess username") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = customUsername.trim().removePrefix("@").isNotBlank() &&
                                selectedFavorites.size < MAX_CHESS_TV_FAVORITES,
                            onClick = {
                                val favorite = ChessTvFavoritePlayers.customLichess(customUsername)
                                if (selectedFavorites.none { it.key == favorite.key }) {
                                    selectedFavorites = selectedFavorites + favorite
                                }
                                customUsername = ""
                            }
                        ) { Text("Add") }
                    }
                    Text(
                        "A username matches broadcasts that identify the player by that handle. Named top players are matched by FIDE ID whenever available.",
                        color = Color(0xFF6B7280),
                        fontSize = 10.sp
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val customFavorites = selectedFavorites.filter { it.key.startsWith("lichess:") }
                        if (customFavorites.isNotEmpty()) {
                            item {
                                Text("Your Lichess players", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                            items(customFavorites, key = { it.key }) { favorite ->
                                ChessTvSetupChoiceRow(
                                    title = favorite.displayName,
                                    subtitle = "Custom Lichess username",
                                    selected = true,
                                    onClick = { selectedFavorites = selectedFavorites.filterNot { it.key == favorite.key } }
                                )
                            }
                        }
                        item {
                            Text("Top players", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        items(ChessTvFavoritePlayers.suggestions, key = { it.key }) { favorite ->
                            val selected = selectedFavorites.any { it.key == favorite.key }
                            ChessTvSetupChoiceRow(
                                title = favorite.displayName,
                                subtitle = "FIDE ${favorite.fideId}",
                                selected = selected,
                                enabled = selected || selectedFavorites.size < MAX_CHESS_TV_FAVORITES,
                                onClick = {
                                    selectedFavorites = if (selected) {
                                        selectedFavorites.filterNot { it.key == favorite.key }
                                    } else {
                                        selectedFavorites + favorite
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step == ChessTvSetupStep.COUNTRY) {
                TextButton(onClick = { step = ChessTvSetupStep.FAVORITES }) { Text("Next") }
            } else {
                TextButton(
                    onClick = {
                        onSave(
                            ChessTvFollowProfile(
                                setupComplete = true,
                                countryId = selectedCountryId,
                                favorites = selectedFavorites.take(MAX_CHESS_TV_FAVORITES)
                            )
                        )
                    }
                ) { Text("Save") }
            }
        },
        dismissButton = {
            if (step == ChessTvSetupStep.FAVORITES) {
                TextButton(onClick = { step = ChessTvSetupStep.COUNTRY }) { Text("Back") }
            } else {
                TextButton(onClick = onDismiss) { Text(if (initialProfile.setupComplete) "Cancel" else "Not now") }
            }
        }
    )
}

@Composable
private fun ChessTvSetupChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = when {
            selected -> Color(0xFFDCFCE7)
            enabled -> Color(0xFFF8FAFC)
            else -> Color(0xFFE5E7EB)
        },
        border = if (selected) BorderStroke(1.5.dp, Color(0xFF15803D)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (enabled) Color(0xFF111827) else Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(subtitle, color = Color(0xFF64748B), fontSize = 9.sp)
            }
            Spacer(Modifier.padding(horizontal = 3.dp))
            if (selected) Text("✓", color = Color(0xFF15803D), fontWeight = FontWeight.Black)
        }
    }
}
