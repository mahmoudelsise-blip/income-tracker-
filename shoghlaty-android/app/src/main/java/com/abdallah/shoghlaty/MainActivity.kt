package com.abdallah.shoghlaty

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class WorkEntry(
    val id: Long,
    val date: String,
    val hours: Double,
    val rate: Double,
    val income: Double,
    val note: String
)

private data class ExpenseEntry(
    val id: Long,
    val date: String,
    val amount: Double,
    val note: String
)

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 24, 32)
    private val panel = Color.rgb(26, 38, 48)
    private val panel2 = Color.rgb(32, 47, 58)
    private val mint = Color.rgb(66, 214, 176)
    private val white = Color.rgb(241, 246, 248)
    private val muted = Color.rgb(155, 173, 183)
    private val red = Color.rgb(255, 125, 125)
    private val prefs by lazy { getSharedPreferences("shoghlaty_data", MODE_PRIVATE) }
    private val works = mutableListOf<WorkEntry>()
    private val expenses = mutableListOf<ExpenseEntry>()
    private var monthlyTarget = 30000.0
    private var selectedDate = today()
    private var root: LinearLayout? = null
    private var page = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        loadData()
        buildShell()
        showPage("home")
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(17), dp(20), dp(12))
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text("شغلتي", 25f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        val tag = text("متابعة دخلك", 12f, mint, true).apply {
            background = rounded(panel2, 30)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        titleRow.addView(tag)
        header.addView(titleRow)
        header.addView(text("نظّم شغلك واعرف فلوسك بتروح فين", 13f, muted).apply {
            setPadding(0, dp(5), 0, 0)
        })
        root?.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(18))
        }
        scroll.addView(content)
        root?.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(9), dp(8), dp(9))
            setBackgroundColor(Color.rgb(20, 30, 39))
            gravity = Gravity.CENTER
        }
        val tabs = listOf(
            "home" to "الرئيسية",
            "work" to "إضافة شغل",
            "expense" to "مصروف",
            "reports" to "التقارير"
        )
        tabs.forEach { (key, label) ->
            val b = TextView(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(dp(4), dp(12), dp(4), dp(12))
                setOnClickListener { showPage(key) }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root?.addView(nav)
        setContentView(root)
    }

    private fun showPage(which: String) {
        page = which
        val scroll = root?.getChildAt(1) as? ScrollView ?: return
        val content = scroll.getChildAt(0) as? LinearLayout ?: return
        content.removeAllViews()
        when (which) {
            "home" -> dashboard(content)
            "work" -> workForm(content)
            "expense" -> expenseForm(content)
            "reports" -> reports(content)
        }
        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun dashboard(parent: LinearLayout) {
        val month = currentMonth()
        val monthWorks = works.filter { it.date.startsWith(month) }
        val monthExpenses = expenses.filter { it.date.startsWith(month) }
        val income = monthWorks.sumOf { it.income }
        val spent = monthExpenses.sumOf { it.amount }
        val net = income - spent
        val hours = monthWorks.sumOf { it.hours }

        sectionTitle(parent, "ملخص الشهر", "أرقامك من أول الشهر لحد النهارده")
        val goalCard = card(parent, panel2)
        val goalTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        goalTop.addView(text("الهدف الشهري", 14f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        val edit = text("تعديل", 12f, mint, true).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { editTarget() }
        }
        goalTop.addView(edit)
        goalCard.addView(goalTop)
        goalCard.addView(text("${money(income)} من ${money(monthlyTarget)} جنيه", 20f, white, true).apply {
            setPadding(0, dp(10), 0, dp(10))
        })
        val progress = if (monthlyTarget > 0) (income / monthlyTarget).coerceIn(0.0, 1.0).toFloat() else 0f
        val progressTrack = LinearLayout(this).apply {
            background = rounded(Color.rgb(55, 70, 80), 20)
            clipToOutline = true
        }
        val progressFill = View(this).apply { background = rounded(mint, 20) }
        progressTrack.addView(progressFill, LinearLayout.LayoutParams(0, dp(9), progress.coerceAtLeast(0.001f)))
        if (progress < 1f) progressTrack.addView(View(this), LinearLayout.LayoutParams(0, dp(9), (1f - progress).coerceAtLeast(0.001f)))
        goalCard.addView(progressTrack)
        goalCard.addView(text("${(income / monthlyTarget.coerceAtLeast(1.0) * 100).toInt()}٪ من الهدف", 12f, mint).apply {
            setPadding(0, dp(8), 0, 0)
        })

        sectionTitle(parent, "حركة الفلوس", "إجمالي الشهر الحالي")
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("الدخل", income, mint), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        stats.addView(statCard("المصروفات", spent, red), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        parent.addView(stats)
        val netCard = card(parent, panel)
        netCard.addView(text("الصافي بعد المصروفات", 13f, muted))
        netCard.addView(text("${money(net)} جنيه", 23f, if (net >= 0) mint else red, true).apply {
            setPadding(0, dp(7), 0, 0)
        })
        val hoursCard = card(parent, panel)
        hoursCard.addView(text("ساعات الشغل هذا الشهر", 13f, muted))
        hoursCard.addView(text("${number(hours)} ساعة", 20f, white, true).apply { setPadding(0, dp(5), 0, 0) })

        sectionTitle(parent, "آخر تسجيلات الشغل", "أحدث 5 عمليات")
        val recent = works.sortedByDescending { it.id }.take(5)
        if (recent.isEmpty()) emptyState(parent, "لسه مفيش شغل متسجل", "ابدأ بإضافة ساعات الشغل وسعر الساعة")
        recent.forEach { workRow(parent, it) }
    }

    private fun workForm(parent: LinearLayout) {
        sectionTitle(parent, "تسجيل يوم شغل", "اكتب الساعات وسعر الساعة والدخل هيتحسب تلقائيًا")
        var date = today()
        val dateButton = dateButton(parent, date) { picked -> date = picked }
        val hours = input(parent, "عدد ساعات الشغل", "مثال: 8", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val rate = input(parent, "سعر الساعة بالجنيه", "مثال: 75", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val note = input(parent, "ملاحظات (اختياري)", "مثال: شيفت صباحي أو مكان الشغل", InputType.TYPE_CLASS_TEXT)
        val preview = card(parent, panel2)
        preview.addView(text("الدخل المتوقع", 13f, muted))
        val previewValue = text("0 جنيه", 23f, mint, true).apply { setPadding(0, dp(6), 0, 0) }
        preview.addView(previewValue)
        val update = {
            val h = hours.text.toString().toDoubleOrNull() ?: 0.0
            val r = rate.text.toString().toDoubleOrNull() ?: 0.0
            previewValue.text = "${money(h * r)} جنيه"
        }
        hours.addTextChangedListener(SimpleWatcher { update() })
        rate.addTextChangedListener(SimpleWatcher { update() })
        val save = primaryButton("حفظ تسجيل الشغل")
        save.setOnClickListener {
            val h = hours.text.toString().toDoubleOrNull()
            val r = rate.text.toString().toDoubleOrNull()
            if (h == null || r == null || h <= 0 || r < 0) {
                toast("اكتب عدد ساعات وسعر ساعة صحيح")
                return@setOnClickListener
            }
            works.add(0, WorkEntry(System.currentTimeMillis(), date, h, r, h * r, note.text.toString().trim()))
            saveData()
            toast("تم حفظ الشغل بنجاح")
            showPage("home")
        }
        parent.addView(save)
    }

    private fun expenseForm(parent: LinearLayout) {
        sectionTitle(parent, "تسجيل مصروف", "سجل أي مبلغ بتصرفه عشان تعرف صافي دخلك")
        var date = today()
        dateButton(parent, date) { picked -> date = picked }
        val amount = input(parent, "قيمة المصروف بالجنيه", "مثال: 120", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val note = input(parent, "سبب المصروف", "مثال: مواصلات أو أكل أو فواتير", InputType.TYPE_CLASS_TEXT)
        val save = primaryButton("حفظ المصروف")
        save.setOnClickListener {
            val value = amount.text.toString().toDoubleOrNull()
            if (value == null || value <= 0) {
                toast("اكتب قيمة مصروف صحيحة")
                return@setOnClickListener
            }
            expenses.add(0, ExpenseEntry(System.currentTimeMillis(), date, value, note.text.toString().trim()))
            saveData()
            toast("تم حفظ المصروف")
            showPage("home")
        }
        parent.addView(save)
    }

    private fun reports(parent: LinearLayout) {
        val month = currentMonth()
        val monthWorks = works.filter { it.date.startsWith(month) }
        val monthExpenses = expenses.filter { it.date.startsWith(month) }
        sectionTitle(parent, "تقارير الشهر", "تفاصيل الدخل وساعات الشغل والمصروفات")
        val summary = card(parent, panel2)
        summary.addView(text("إجمالي ساعات الشغل", 13f, muted))
        summary.addView(text("${number(monthWorks.sumOf { it.hours })} ساعة", 19f, white, true).apply { setPadding(0, dp(4), 0, dp(10)) })
        summary.addView(text("إجمالي الدخل: ${money(monthWorks.sumOf { it.income })} جنيه", 14f, mint, true))
        summary.addView(text("إجمالي المصروفات: ${money(monthExpenses.sumOf { it.amount })} جنيه", 14f, red, true).apply { setPadding(0, dp(6), 0, 0) })

        sectionTitle(parent, "أيام الشغل", "مجموع كل يوم بشكل منفصل")
        val grouped = monthWorks.groupBy { it.date }.toSortedMap(compareByDescending { it })
        if (grouped.isEmpty()) emptyState(parent, "مفيش بيانات للشهر ده", "سجل أول يوم شغل وهيظهر هنا")
        grouped.forEach { (date, entries) ->
            val totalHours = entries.sumOf { it.hours }
            val totalIncome = entries.sumOf { it.income }
            val box = card(parent, panel)
            box.addView(text(displayDate(date), 14f, white, true))
            box.addView(text("${number(totalHours)} ساعة  •  ${money(totalIncome)} جنيه", 13f, mint).apply { setPadding(0, dp(5), 0, 0) })
            entries.forEach { entry ->
                val details = if (entry.note.isBlank()) "${number(entry.hours)} س × ${money(entry.rate)} جنيه" else "${number(entry.hours)} س × ${money(entry.rate)} جنيه — ${entry.note}"
                box.addView(text(details, 11f, muted).apply { setPadding(0, dp(4), 0, 0) })
            }
        }

        sectionTitle(parent, "سجل المصروفات", "المصروفات المسجلة خلال الشهر")
        val sortedExpenses = monthExpenses.sortedByDescending { it.id }
        if (sortedExpenses.isEmpty()) emptyState(parent, "مفيش مصروفات مسجلة", "أي مصروف تضيفه هيظهر هنا")
        sortedExpenses.forEach { expense ->
            val box = card(parent, panel)
            box.addView(text(expense.note.ifBlank { "مصروف بدون وصف" }, 14f, white, true))
            box.addView(text("${displayDate(expense.date)}  •  ${money(expense.amount)} جنيه", 12f, red).apply { setPadding(0, dp(4), 0, 0) })
        }
    }

    private fun sectionTitle(parent: LinearLayout, title: String, subtitle: String) {
        parent.addView(text(title, 18f, white, true).apply { setPadding(0, dp(14), 0, dp(4)) })
        parent.addView(text(subtitle, 12f, muted).apply { setPadding(0, 0, 0, dp(10)) })
    }

    private fun card(parent: LinearLayout, color: Int): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color, 18)
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return box
    }

    private fun statCard(title: String, value: Double, color: Int): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 16)
            setPadding(dp(13), dp(14), dp(13), dp(14))
        }
        box.addView(text(title, 12f, muted))
        box.addView(text(money(value), 17f, color, true).apply { setPadding(0, dp(7), 0, 0) })
        box.addView(text("جنيه", 10f, muted).apply { setPadding(0, dp(2), 0, 0) })
        return box
    }

    private fun workRow(parent: LinearLayout, work: WorkEntry) {
        val box = card(parent, panel)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(displayDate(work.date), 13f, white, true))
        val detail = "${number(work.hours)} ساعة × ${money(work.rate)} جنيه"
        left.addView(text(if (work.note.isBlank()) detail else "$detail • ${work.note}", 11f, muted).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("+${money(work.income)}", 15f, mint, true))
        box.addView(row)
    }

    private fun emptyState(parent: LinearLayout, title: String, subtitle: String) {
        val box = card(parent, panel)
        box.gravity = Gravity.CENTER
        box.addView(text(title, 14f, white, true).apply { gravity = Gravity.CENTER })
        box.addView(text(subtitle, 11f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
    }

    private fun input(parent: LinearLayout, label: String, hint: String, type: Int): EditText {
        parent.addView(text(label, 13f, white, true).apply { setPadding(0, dp(9), 0, dp(6)) })
        return EditText(this).apply {
            textSize = 15f
            setTextColor(white)
            setHintTextColor(muted)
            this.hint = hint
            inputType = type
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(panel, 13)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(4) })
        }
    }

    private fun dateButton(parent: LinearLayout, initial: String, onSelected: (String) -> Unit): TextView {
        parent.addView(text("تاريخ التسجيل", 13f, white, true).apply { setPadding(0, dp(9), 0, dp(6)) })
        val button = text("📅  ${displayDate(initial)}  — تغيير التاريخ", 14f, mint, true).apply {
            background = rounded(panel, 13)
            setPadding(dp(13), dp(15), dp(13), dp(15))
            setOnClickListener {
                val cal = Calendar.getInstance()
                val parts = selectedDate.split("-")
                if (parts.size == 3) {
                    cal.set(parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR),
                        (parts[1].toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1,
                        parts[2].toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH))
                }
                DatePickerDialog(this@MainActivity, { _, y, m, d ->
                    selectedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    text = "📅  ${displayDate(selectedDate)}  — تغيير التاريخ"
                    onSelected(selectedDate)
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        selectedDate = initial
        return button
    }

    private fun primaryButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.rgb(10, 35, 31))
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(mint, 14)
        isAllCaps = false
        setPadding(dp(14), dp(9), dp(14), dp(9))
        val params = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(18); bottomMargin = dp(12) }
        layoutParams = params
    }

    private fun editTarget() {
        val field = EditText(this).apply {
            setText(monthlyTarget.toLong().toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(white)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            hint = "الهدف بالجنيه"
        }
        AlertDialog.Builder(this)
            .setTitle("تعديل الهدف الشهري")
            .setMessage("اكتب المبلغ اللي عايز توصله كل شهر")
            .setView(field)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                val value = field.text.toString().toDoubleOrNull()
                if (value != null && value > 0) {
                    monthlyTarget = value
                    saveData()
                    showPage("home")
                } else toast("اكتب مبلغ صحيح")
            }.show()
    }

    private fun loadData() {
        monthlyTarget = prefs.getString("target", "30000")?.toDoubleOrNull() ?: 30000.0
        val workArray = runCatching { JSONArray(prefs.getString("works", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until workArray.length()) {
            val o = workArray.optJSONObject(i) ?: continue
            works.add(WorkEntry(o.optLong("id"), o.optString("date"), o.optDouble("hours"), o.optDouble("rate"), o.optDouble("income"), o.optString("note")))
        }
        val expenseArray = runCatching { JSONArray(prefs.getString("expenses", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until expenseArray.length()) {
            val o = expenseArray.optJSONObject(i) ?: continue
            expenses.add(ExpenseEntry(o.optLong("id"), o.optString("date"), o.optDouble("amount"), o.optString("note")))
        }
    }

    private fun saveData() {
        val w = JSONArray()
        works.forEach { item ->
            w.put(JSONObject().apply {
                put("id", item.id); put("date", item.date); put("hours", item.hours)
                put("rate", item.rate); put("income", item.income); put("note", item.note)
            })
        }
        val e = JSONArray()
        expenses.forEach { item ->
            e.put(JSONObject().apply {
                put("id", item.id); put("date", item.date); put("amount", item.amount); put("note", item.note)
            })
        }
        prefs.edit().putString("works", w.toString()).putString("expenses", e.toString())
            .putString("target", monthlyTarget.toString()).apply()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun money(value: Double): String = DecimalFormat("#,##0.##").format(value)
    private fun number(value: Double): String = DecimalFormat("#,##0.##").format(value)
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun currentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    private fun displayDate(value: String): String {
        val parts = value.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else value
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}


private class SimpleWatcher(private val onChanged: () -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
    override fun afterTextChanged(s: Editable?) = Unit
}
