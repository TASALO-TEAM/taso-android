@file:OptIn(ExperimentalMaterial3Api::class)

package com.tasalo.android.ui.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.tasalo.android.ui.components.floatingContentPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasalo.android.domain.Converter
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.metaFor
import com.tasalo.android.ui.UiState
import com.tasalo.android.ui.components.ErrorState
import com.tasalo.android.ui.components.GlassCard
import com.tasalo.android.ui.components.SkeletonBlock
import com.tasalo.android.ui.theme.LocalGlass
import com.tasalo.android.ui.theme.TasaloMono
import com.tasalo.android.util.Format

/**
 * Calculadora de tasas: usa las tasas que la app ya tiene (sin red propia). Por defecto El Toque, USD → CUP.
 * Arriba se elige la fuente, en medio se escribe el monto y se invierte el par, y abajo, a modo de factura,
 * el mismo monto expresado en el resto de monedas de esa fuente.
 */
@Composable
fun CalculatorScreen(state: UiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    var sourceId by rememberSaveable { mutableStateOf(state.settings.defaultSource.id) }
    var amountText by rememberSaveable { mutableStateOf("1") }
    var fromCode by rememberSaveable { mutableStateOf("USD") }
    var toCode by rememberSaveable { mutableStateOf(Converter.CUP) }

    val context = LocalContext.current
    val source = Source.fromId(sourceId) ?: Source.ELTOQUE
    val snapshot = state.rates
    val hidden = state.settings.hidden
    val table = remember(snapshot, source, hidden) {
        Converter.table(snapshot?.bySource?.get(source).orEmpty().filter { !state.settings.isHidden(source, it.currency) }, source)
    }
    val codes = remember(table, source) { Converter.codes(table, source) }

    // Si la fuente elegida no trae la moneda guardada, se cae a una válida sin tocar lo que escribió la persona.
    val from = if (fromCode in codes) fromCode else codes.firstOrNull { it != Converter.CUP } ?: Converter.CUP
    val to = (if (toCode in codes) toCode else if (Converter.CUP in codes) Converter.CUP else codes.firstOrNull { it != from } ?: Converter.CUP)
        .let { t -> if (t == from) codes.firstOrNull { it != from } ?: t else t }

    val amount = Format.parseAmount(amountText)
    val result = amount?.let { Converter.convert(it, from, to, table) }

    fun pickFrom(code: String) {
        if (code == to) toCode = from
        fromCode = code
    }

    fun pickTo(code: String) {
        if (code == from) fromCode = to
        toCode = code
    }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = floatingContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Calculadora", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (snapshot != null) "Tasas de ${Format.relative(snapshot.fetchedAt, state.now)}" else "Sin tasas aún",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { CalcSourceSelector(source) { sourceId = it.id } }

            if (snapshot == null) {
                item {
                    if (!state.loaded || state.refreshing) {
                        SkeletonBlock(height = 200)
                    } else {
                        ErrorState("No hay tasas para calcular todavía.", onRetry = onRefresh)
                    }
                }
            } else {
                item {
                    ConverterCard(
                        amountText = amountText,
                        onAmountChange = { amountText = sanitize(it) },
                        from = from,
                        to = to,
                        codes = codes,
                        source = source,
                        resultText = result?.let(Format::amount) ?: "—",
                        unitRate = Converter.convert(1.0, from, to, table)?.let(Format::amount),
                        sourceTitle = source.title,
                        onPickFrom = ::pickFrom,
                        onPickTo = ::pickTo,
                        onSwap = {
                            fromCode = to
                            toCode = from
                        },
                    )
                }
                item {
                    val rowCodes = codes.filter { it != from }
                    val rows = rowCodes.map { it to Converter.convert(amount ?: 0.0, from, it, table) }
                    val receipt = amount?.let { Receipt.build(it, from, source.title, table, rowCodes) }
                    InvoiceCard(
                        amount = amount,
                        source = source,
                        from = from,
                        to = to,
                        rows = rows,
                        onPick = ::pickTo,
                        onCopy = { receipt?.let { copyText(context, it) } },
                        onShare = { receipt?.let { shareText(context, it) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcSourceSelector(selected: Source, onSelect: (Source) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Source.entries.forEachIndexed { index, source ->
            SegmentedButton(
                selected = source == selected,
                onClick = { onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index, Source.entries.size),
            ) { Text(source.title) }
        }
    }
}

@Composable
private fun ConverterCard(
    amountText: String,
    onAmountChange: (String) -> Unit,
    from: String,
    to: String,
    codes: List<String>,
    source: Source,
    resultText: String,
    unitRate: String?,
    sourceTitle: String,
    onPickFrom: (String) -> Unit,
    onPickTo: (String) -> Unit,
    onSwap: () -> Unit,
) {
    val glass = LocalGlass.current
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val amountStyle = TextStyle(
        fontFamily = TasaloMono,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
    )
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyPicker(from, codes, source, onPickFrom)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = amountText,
                    onValueChange = onAmountChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "Monto en $from" },
                    textStyle = amountStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (amountText.isEmpty()) {
                                Text("0", style = amountStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            }
                            inner()
                        }
                    },
                )
                if (amountText.isNotEmpty()) {
                    IconButton(onClick = {
                        onAmountChange("")
                        focusRequester.requestFocus()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Borrar monto")
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(glass.border))
                FilledTonalIconButton(onClick = onSwap, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Invertir monedas")
                }
                Box(Modifier.weight(1f).height(1.dp).background(glass.border))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyPicker(to, codes, source, onPickTo)
                Spacer(Modifier.width(12.dp))
                Text(
                    resultText,
                    modifier = Modifier.weight(1f),
                    fontFamily = TasaloMono,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }

            if (unitRate != null) {
                Text(
                    "1 $from = $unitRate $to · $sourceTitle",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun CurrencyPicker(selected: String, options: List<String>, source: Source, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val glass = LocalGlass.current
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(16.dp),
            color = glass.accentSoft,
            border = BorderStroke(1.dp, glass.borderAccent),
            modifier = Modifier.semantics { contentDescription = "Moneda ${nameOf(selected, source)}. Cambiar" },
        ) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(flagOf(selected, source), fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(selected, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text("${flagOf(code, source)}  $code · ${nameOf(code, source)}") },
                    onClick = {
                        onSelect(code)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    amount: Double?,
    source: Source,
    from: String,
    to: String,
    rows: List<Pair<String, Double?>>,
    onPick: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val glass = LocalGlass.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Equivale a",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${amount?.let(Format::amount) ?: "—"} $from",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar resumen") }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Compartir resumen") }
            }
            rows.forEach { (code, value) ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(glass.border))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(code) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(flagOf(code, source), fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(code, fontWeight = FontWeight.Bold)
                        Text(
                            nameOf(code, source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        value?.let(Format::amount) ?: "—",
                        fontFamily = TasaloMono,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (code == to) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun flagOf(code: String, source: Source): String =
    if (source != Source.QVAPAY && code == Converter.CUP) "🇨🇺" else metaFor(source, code)?.flag.orEmpty()

private fun nameOf(code: String, source: Source): String =
    if (source != Source.QVAPAY && code == Converter.CUP) "Peso cubano" else metaFor(source, code)?.name ?: code

/** Deja solo dígitos y un separador decimal (coma o punto, se muestra coma). */
private fun sanitize(input: String): String {
    val out = StringBuilder()
    var hasSeparator = false
    for (c in input) {
        when {
            c.isDigit() -> out.append(c)
            (c == ',' || c == '.') && !hasSeparator -> {
                hasSeparator = true
                out.append(',')
            }
        }
    }
    return out.take(14).toString()
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("TASALO", text))
    // Desde Android 13 el sistema ya muestra su propio aviso al copiar.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Resumen copiado", Toast.LENGTH_SHORT).show()
    }
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Compartir resumen")) }
}
