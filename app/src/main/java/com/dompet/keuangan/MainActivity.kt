package com.dompet.keuangan

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : android.app.Activity() {
    private class Tx(val out: Boolean, val amount: Long, val cat: String, val note: String, val time: Long)

    private lateinit var container: FrameLayout
    private var current: View? = null
    private var onDashboard = true
    private val ease = DecelerateInterpolator(1.5f)
    private val prefs by lazy { getSharedPreferences("dompet", Context.MODE_PRIVATE) }
    private val txs = ArrayList<Tx>()
    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id","ID"))
    private val palette = intArrayOf(
        Color.rgb(37,99,235), Color.rgb(249,115,22), Color.rgb(22,163,74), Color.rgb(147,51,234),
        Color.rgb(219,39,119), Color.rgb(13,148,136), Color.rgb(202,138,4), Color.rgb(100,116,139)
    )
    private var saldo = 0L
    private var masuk = 0L
    private var keluar = 0L

    private fun money(v: Long): String =
        NumberFormat.getCurrencyInstance(Locale("id","ID")).format(v).replace(",00","")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load(); recalc()
        container = FrameLayout(this)
        container.setBackgroundColor(Color.WHITE)
        setContentView(container)
        showDashboard(animate = false)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!onDashboard) showDashboard(forward = false) else super.onBackPressed()
    }

    // ---------- Data: riwayat transaksi disimpan sebagai JSON di SharedPreferences ----------
    private fun load() {
        val s = prefs.getString("tx", null)
        if (s != null) {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                txs.add(Tx(o.getBoolean("o"), o.getLong("a"), o.getString("c"), o.optString("n", ""), o.getLong("t")))
            }
        } else { // migrasi dari versi lama yang hanya menyimpan 3 total
            val now = System.currentTimeMillis()
            val m = prefs.getLong("masuk", 0); val k = prefs.getLong("keluar", 0)
            if (m > 0) txs.add(Tx(false, m, "Saldo awal", "Migrasi data lama", now))
            if (k > 0) txs.add(Tx(true, k, "Lainnya", "Migrasi data lama", now))
            persist()
        }
    }

    private fun persist() {
        val a = JSONArray()
        for (t in txs) a.put(JSONObject().put("o", t.out).put("a", t.amount).put("c", t.cat).put("n", t.note).put("t", t.time))
        prefs.edit().putString("tx", a.toString()).apply()
    }

    private fun recalc() {
        masuk = 0; keluar = 0
        for (t in txs) if (t.out) keluar += t.amount else masuk += t.amount
        saldo = masuk - keluar
    }

    // ---------- Transisi antar layar: hanya translasi + alpha pada hardware layer ----------
    private fun show(next: View, forward: Boolean, animate: Boolean) {
        while (container.childCount > 1) container.removeViewAt(0)
        val old = current
        current = next
        container.addView(next)
        if (!animate || old == null) {
            old?.let { container.removeView(it) }
            return
        }
        val shift = resources.displayMetrics.widthPixels * 0.25f * (if (forward) 1f else -1f)
        next.translationX = shift
        next.alpha = 0f
        old.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        next.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        old.animate().translationX(-shift).alpha(0f).setDuration(240).setInterpolator(ease)
            .withEndAction { container.removeView(old); old.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()
        next.animate().translationX(0f).alpha(1f).setDuration(240).setInterpolator(ease)
            .withEndAction { next.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()
    }

    private fun tv(text:String, size:Float=16f, bold:Boolean=false): TextView {
        val t=TextView(this); t.text=text; t.textSize=size
        t.setTextColor(Color.rgb(20,30,45)); t.setPadding(0,8,0,8)
        if(bold) t.setTypeface(null,1)
        return t
    }

    private fun page(): LinearLayout {
        val root=LinearLayout(this); root.orientation=LinearLayout.VERTICAL
        root.setPadding(24,30,24,18); root.setBackgroundColor(Color.WHITE)
        return root
    }

    private fun scrolled(root: View): ScrollView {
        val s = ScrollView(this)
        s.isFillViewport = true
        s.setBackgroundColor(Color.WHITE)
        s.addView(root)
        return s
    }

    // ---------- Dashboard ----------
    private fun showDashboard(forward: Boolean = false, animate: Boolean = true) {
        onDashboard = true
        val root = page()
        root.addView(tv("Dompet",30f,true))
        root.addView(tv("Keuangan pribadi • offline-first",14f))

        val balance=tv("Saldo\n${money(saldo)}",25f,true)
        balance.setPadding(18,22,18,22)
        balance.setBackgroundColor(Color.rgb(239,246,255))
        root.addView(balance)

        val grid=LinearLayout(this); grid.orientation=LinearLayout.HORIZONTAL
        val income=tv("Masuk\n${money(masuk)}",16f,true)
        val expense=tv("Keluar\n${money(keluar)}",16f,true)
        income.setPadding(12,18,12,18); expense.setPadding(12,18,12,18)
        grid.addView(income,LinearLayout.LayoutParams(0,-2,1f))
        grid.addView(expense,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(grid)

        root.addView(tv("Ringkasan",19f,true))
        root.addView(tv("Sisa yang dapat ditabung: ${money((masuk-keluar).coerceAtLeast(0))}",16f))

        val ring = RingChart(this)
        ring.setTarget(if (masuk > 0) keluar.toFloat() / masuk else 0f)
        val rp = LinearLayout.LayoutParams(dp(180), dp(180))
        rp.gravity = Gravity.CENTER_HORIZONTAL; rp.topMargin = dp(8); rp.bottomMargin = dp(8)
        root.addView(ring, rp)

        val add=Button(this); add.text="+ Tambah transaksi"; add.setOnClickListener { showTransaction() }
        root.addView(add)

        val report=Button(this); report.text="Lihat laporan & kategori"; report.setOnClickListener { showReport() }
        root.addView(report)

        val reset=Button(this); reset.text="Reset data"; reset.setOnClickListener {
            AlertDialog.Builder(this).setMessage("Hapus semua transaksi?")
                .setPositiveButton("Hapus") { _, _ -> txs.clear(); persist(); recalc(); showDashboard() }
                .setNegativeButton("Batal", null).show()
        }
        root.addView(reset)
        show(scrolled(root), forward, animate)
    }

    // ---------- Tambah transaksi ----------
    private fun showTransaction() {
        onDashboard = false
        val root = page()
        root.addView(tv("Tambah transaksi",26f,true))
        val type=Spinner(this)
        type.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("Uang masuk","Uang keluar"))
        root.addView(type)
        val amount=EditText(this); amount.hint="Nominal (Rp)"; amount.inputType=2
        root.addView(amount)
        val cats = arrayOf("Makanan","Transport","Belanja","Tagihan","Hiburan","Kesehatan","Gaji","Lainnya")
        val category=AutoCompleteTextView(this); category.hint="Kategori, contoh: Makanan"
        category.setAdapter(ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line,cats))
        category.threshold = 1
        category.setOnClickListener { category.showDropDown() }
        root.addView(category)
        val note=EditText(this); note.hint="Catatan"
        root.addView(note)
        val saveBtn=Button(this); saveBtn.text="Simpan"
        saveBtn.setOnClickListener {
            val n=amount.text.toString().toLongOrNull()
            if(n==null || n<=0){ amount.error="Masukkan nominal yang benar"; return@setOnClickListener }
            val c=category.text.toString().trim().lowercase().replaceFirstChar { it.uppercase() }.ifEmpty { "Lainnya" }
            txs.add(Tx(type.selectedItemPosition==1, n, c, note.text.toString().trim(), System.currentTimeMillis()))
            persist(); recalc(); showDashboard(forward = false)
            Toast.makeText(this,"Transaksi tersimpan",Toast.LENGTH_SHORT).show()
        }
        root.addView(saveBtn)
        val back=Button(this); back.text="Batal"; back.setOnClickListener { showDashboard(forward = false) }
        root.addView(back)
        show(scrolled(root), true, true)
    }

    // ---------- Laporan: grafik donat per kategori + riwayat ----------
    private fun showReport(forward: Boolean = true, animate: Boolean = true) {
        onDashboard = false
        val root = page()
        root.addView(tv("Laporan & kategori",26f,true))
        root.addView(tv("Pengeluaran per kategori",16f))

        val sums = LinkedHashMap<String, Long>()
        for (t in txs) if (t.out) sums[t.cat] = (sums[t.cat] ?: 0L) + t.amount
        val sorted = sums.entries.sortedByDescending { it.value }

        val donut = DonutChart(this)
        donut.setData(FloatArray(sorted.size) { sorted[it].value.toFloat() },
                      IntArray(sorted.size) { palette[it % palette.size] })
        val lp = LinearLayout.LayoutParams(dp(210), dp(210))
        lp.gravity = Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(8); lp.bottomMargin = dp(8)
        root.addView(donut, lp)

        for ((i, e) in sorted.withIndex()) {
            val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val dot = tv("●", 16f); dot.setTextColor(palette[i % palette.size]); dot.setPadding(0,8,dp(8),8)
            val pct = Math.round(e.value * 100f / keluar)
            row.addView(dot)
            row.addView(tv(e.key, 16f), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(tv("$pct% • ${money(e.value)}", 14f))
            root.addView(row)
        }

        val head = tv("Riwayat transaksi", 19f, true); head.setPadding(0, dp(16), 0, 8)
        root.addView(head)
        if (txs.isEmpty()) root.addView(tv("Belum ada transaksi.", 14f))
        else root.addView(tv("Tahan lama pada transaksi untuk menghapus.", 12f))
        for (t in txs.asReversed()) root.addView(txRow(t))

        val back=Button(this); back.text="Kembali"; back.setOnClickListener { showDashboard(forward = false) }
        root.addView(back)
        show(scrolled(root), forward, animate)
    }

    private fun txRow(t: Tx): View {
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(6), 0, dp(6))
        val left = LinearLayout(this); left.orientation = LinearLayout.VERTICAL
        left.addView(tv(t.cat, 16f, true))
        val sub = tv((if (t.note.isEmpty()) "" else t.note + " • ") + dateFmt.format(Date(t.time)), 12f)
        sub.setTextColor(Color.rgb(100,116,139))
        left.addView(sub)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val amt = tv((if (t.out) "- " else "+ ") + money(t.amount), 15f, true)
        amt.setTextColor(if (t.out) Color.rgb(220,38,38) else Color.rgb(22,163,74))
        row.addView(amt)
        row.setOnLongClickListener {
            AlertDialog.Builder(this).setMessage("Hapus transaksi ini?")
                .setPositiveButton("Hapus") { _, _ -> txs.remove(t); persist(); recalc(); showReport(animate = false) }
                .setNegativeButton("Batal", null).show()
            true
        }
        return row
    }
}

// ---------- Grafik (onDraw tanpa alokasi objek; animasi lewat ValueAnimator) ----------
private abstract class AnimatedView(ctx: Context) : View(ctx) {
    protected var progress = 0f
    private var anim: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800; startDelay = 120; interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    protected fun fit(oval: RectF, stroke: Float) {
        val side = minOf(width, height) - stroke
        val l = (width - side) / 2f
        val t = (height - side) / 2f
        oval.set(l, t, l + side, t + side)
    }
}

private class RingChart(ctx: Context) : AnimatedView(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private var target = 0f
    private var lastPct = -1
    private var text = "0%"

    init {
        val d = ctx.resources.displayMetrics.density
        track.style = Paint.Style.STROKE; track.strokeWidth = 16 * d; track.color = Color.rgb(226,232,240)
        arc.style = Paint.Style.STROKE; arc.strokeWidth = 16 * d; arc.strokeCap = Paint.Cap.ROUND
        label.textAlign = Paint.Align.CENTER; label.textSize = 30 * d; label.isFakeBoldText = true
        label.color = Color.rgb(20,30,45)
        sub.textAlign = Paint.Align.CENTER; sub.textSize = 12 * d; sub.color = Color.rgb(100,116,139)
    }

    fun setTarget(p: Float) {
        target = p.coerceIn(0f, 1f)
        arc.color = if (target > 0.8f) Color.rgb(220,38,38) else Color.rgb(37,99,235)
    }

    override fun onDraw(c: Canvas) {
        fit(oval, track.strokeWidth)
        c.drawOval(oval, track)
        val v = target * progress
        c.drawArc(oval, -90f, 360f * v, false, arc)
        val pct = (v * 100).toInt()
        if (pct != lastPct) { lastPct = pct; text = "$pct%" }
        val cy = height / 2f
        c.drawText(text, width / 2f, cy - (label.ascent() + label.descent()) / 2f, label)
        c.drawText("dari pemasukan", width / 2f, cy + track.strokeWidth * 0.9f, sub)
    }
}

private class DonutChart(ctx: Context) : AnimatedView(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private var values = FloatArray(0)
    private var colors = IntArray(0)
    private var total = 0f

    init {
        val d = ctx.resources.displayMetrics.density
        track.style = Paint.Style.STROKE; track.strokeWidth = 28 * d; track.color = Color.rgb(226,232,240)
        seg.style = Paint.Style.STROKE; seg.strokeWidth = 28 * d
        label.textAlign = Paint.Align.CENTER; label.textSize = 14 * d; label.color = Color.rgb(100,116,139)
    }

    fun setData(v: FloatArray, c: IntArray) { values = v; colors = c; total = v.sum() }

    override fun onDraw(c: Canvas) {
        fit(oval, track.strokeWidth)
        c.drawOval(oval, track)
        val ty = height / 2f - (label.ascent() + label.descent()) / 2f
        if (total <= 0f) { c.drawText("Belum ada data", width / 2f, ty, label); return }
        var start = -90f
        for (i in values.indices) {
            val sweep = 360f * values[i] / total * progress
            seg.color = colors[i]
            c.drawArc(oval, start, sweep, false, seg)
            start += sweep
        }
        c.drawText("Pengeluaran", width / 2f, ty, label)
    }
}
