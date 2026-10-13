@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.tasalo.android.ui.cash

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasalo.android.domain.CashCalc
import com.tasalo.android.domain.CashCurrency
import com.tasalo.android.domain.CashReceipt
import com.tasalo.android.ui.components.AdaptiveSegmentedChoice
import com.tasalo.android.ui.components.GlassCard
import com.tasalo.android.ui.components.floatingContentPadding
import com.tasalo.android.ui.theme.LocalGlass
import com.tasalo.android.ui.theme.TasaloMono
import com.tasalo.android.util.Format
import java.time.LocalDateTime

/**
 * Calculadora de efectivo: se cuentan los billetes de CUP, USD o EUR y se ve el total al instante.
 * No usa red. Las cantidades de cada moneda se conservan al cambiar de pestaña de moneda y al rotar.
 */
@Composable
fun CashScreen(modifier: Modifier = Modifier) {
    var currencyName by rememberSaveable { mutableStateOf(CashCurrency.CUP.name) }
    var saved by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var showSave by rememberSaveable { mutableStateOf(false) }
    var saveAll by rememberSaveable { mutableStateOf(false) }
    var pendingText by rememberSaveable { mutableStateOf("") }

    val currency = CashCurrency.entries.firstOrNull { it.name == currencyName } ?: CashCurrency.CUP
    val counts = remember(saved) { CashCalc.decode(saved) }
    val context = LocalContext.current

    // Con la app a pantalla completa (edge-to-edge) el teclado no redimensiona la ventana: se superpone. Por eso la lista
    // se encoge con imePadding hasta quedar justo encima del teclado (y recupera su alto sola al bajarlo), y mientras
    // el teclado está abierto se quita el hueco de la cápsula flotante, que queda tapada detrás de él.
    val imeVisible = WindowInsets.isImeVisible
    val basePadding = floatingContentPadding()
    val listPadding = if (imeVisible) PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp) else basePadding

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(pendingText.toByteArray(Charsets.UTF_8)) }
                    ?: error("sin salida")
            }.isSuccess
            Toast.makeText(context, if (ok) "Conteo guardado" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier.fillMaxSize().imePadding(),
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Efectivo", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Cuenta tus billetes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AdaptiveSegmentedChoice(
                options = CashCurrency.entries,
                isSelected = { it == currency },
                label = { it.code },
                onSelect = { currencyName = it.name },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val glass = LocalGlass.current
            val last = currency.denominations.lastIndex
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    HeaderRow()
                    currency.denominations.forEachIndexed { index, denomination ->
                        Box(Modifier.fillMaxWidth().height(1.dp).background(glass.border))
                        val key = CashCalc.key(currency, denomination)
                        CashRow(
                            currency = currency,
                            denomination = denomination,
                            text = counts[key].orEmpty(),
                            imeAction = if (index == last) ImeAction.Done else ImeAction.Next,
                            onChange = { text -> saved = CashCalc.encode(counts + (key to text)) },
                        )
                    }
                }
            }
        }
        item { TotalCapsule(currency.code, CashCalc.total(currency, counts)) }
        item {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = counts.isNotEmpty(),
                    modifier = Modifier.weight(1f).widthIn(min = 140.dp).heightIn(min = 48.dp).fillMaxRowHeight(),
                ) { Text("Limpiar") }
                Button(
                    onClick = { showSave = true },
                    modifier = Modifier.weight(1f).widthIn(min = 140.dp).heightIn(min = 48.dp).fillMaxRowHeight(),
                ) { Text("Guardar") }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Poner todo en 0?") },
            text = { Text("Se borran las cantidades de CUP, USD y EUR. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    saved = ""
                    confirmClear = false
                }) { Text("Limpiar todo") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }

    if (showSave) {
        val list = if (saveAll) CashCurrency.entries else listOf(currency)
        val preview = remember(saveAll, currency, counts) {
            CashReceipt.build(list, counts, LocalDateTime.now(), summary = saveAll)
        }
        ModalBottomSheet(
            onDismissRequest = { showSave = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SaveSheet(
                currency = currency,
                saveAll = saveAll,
                onChooseAll = { saveAll = it },
                preview = preview,
                onCancel = { showSave = false },
                onConfirm = {
                    pendingText = CashReceipt.build(list, counts, LocalDateTime.now(), summary = saveAll)
                    val name = "tasalo-efectivo-${if (saveAll) "total" else currency.code.lowercase()}.txt"
                    showSave = false
                    launcher.launch(name)
                },
            )
        }
    }
}

/** Ancho de la casilla de cantidad: crece con la letra del sistema, para que los 6 dígitos nunca se corten. */
@Composable
private fun fieldWidth() = 96.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)

@Composable
private fun HeaderRow() {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Billete", Modifier.widthIn(min = 72.dp), style = style, color = color)
        Text("Cantidad", Modifier.width(fieldWidth()), style = style, color = color, textAlign = TextAlign.End)
        Text("Valor", Modifier.weight(1f).widthIn(min = 96.dp), style = style, color = color, textAlign = TextAlign.End)
    }
}

@Composable
private fun CashRow(
    currency: CashCurrency,
    denomination: Long,
    text: String,
    imeAction: ImeAction,
    onChange: (String) -> Unit,
) {
    val glass = LocalGlass.current
    val focus = LocalFocusManager.current
    val value = CashCalc.rowValue(denomination, text)
    val denominationText = Format.amount(denomination.toDouble())
    val inputStyle = TextStyle(
        fontFamily = TasaloMono,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
    )
    // Si con letra grande no caben las tres celdas, la fila se apila: nunca se recorta ni se reduce el texto.
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                denominationText,
                fontFamily = TasaloMono,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { onChange(CashCalc.sanitize(it)) },
            modifier = Modifier
                .width(fieldWidth())
                .semantics { contentDescription = "Cantidad de billetes de $denominationText ${currency.code}" },
            textStyle = inputStyle,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .border(1.dp, glass.border, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (text.isEmpty()) {
                        Text("0", style = inputStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    inner()
                }
            },
        )
        Box(Modifier.weight(1f).widthIn(min = 96.dp).heightIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                Format.amount(value.toDouble()),
                fontFamily = TasaloMono,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun TotalCapsule(code: String, total: Long) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(percent = 50)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(glass.accentSoft)
            .border(1.dp, glass.borderAccent, shape)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.CenterStart) {
                Text("Total · $code", style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    Format.amount(total.toDouble()),
                    fontFamily = TasaloMono,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SaveSheet(
    currency: CashCurrency,
    saveAll: Boolean,
    onChooseAll: (Boolean) -> Unit,
    preview: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val glass = LocalGlass.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Guardar conteo en .txt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ChoiceRow(
            selected = !saveAll,
            title = "Individual · ${currency.code}",
            subtitle = "Solo la moneda que estás contando",
            onClick = { onChooseAll(false) },
        )
        ChoiceRow(
            selected = saveAll,
            title = "Total",
            subtitle = "CUP, USD y EUR con resumen final",
            onClick = { onChooseAll(true) },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .border(1.dp, glass.border, MaterialTheme.shapes.small)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                preview,
                fontFamily = TasaloMono,
                fontSize = 11.sp,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).widthIn(min = 140.dp).heightIn(min = 48.dp).fillMaxRowHeight(),
            ) { Text("Cancelar") }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).widthIn(min = 140.dp).heightIn(min = 48.dp).fillMaxRowHeight(),
            ) { Text("Guardar archivo") }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(glass.accentSoft) else Modifier)
            .border(1.dp, if (selected) glass.borderAccent else glass.border, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
