/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.mvt.style

import com.asamm.vectormaps.mvt.tile.Feature
import com.asamm.vectormaps.mvt.tile.GeometryType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * What an expression reads: the zoom, the feature and the `let` bindings in scope.
 */
class EvalContext(
    var zoom: Double = 0.0,
    var feature: Feature? = null,
) {

    /**
     * The properties `get` reads for `name`, the first the feature has, such as `name:zh-Hant` and
     * `name:zh` for a text field in the host's language; empty reads `name`.
     */
    internal var localNames: List<String> = emptyList()

    internal val bindings = HashMap<String, Any?>()
}

/**
 * A compiled MapLibre style expression. Values are `Double`, `String`, `Boolean`, [Color], `List`
 * or null.
 */
abstract class Expression {

    abstract fun eval(ctx: EvalContext): Any?

    /**
     * True when the value does not depend on the feature, so a layer can evaluate it once per tile.
     */
    open val isFeatureConstant: Boolean = true

    /**
     * True when the value does not depend on the zoom.
     */
    open val isZoomConstant: Boolean = true

    companion object {

        /**
         * Compiles a JSON value: an expression array, or a constant (literal string, number, ...).
         * An array whose head is not a spec operator is a constant; a spec operator this engine
         * does not implement throws [UnsupportedExpressionException].
         */
        internal fun compile(json: JsonElement): Expression {
            return ExpressionCompiler.compile(json)
        }

        /**
         * Compiles a layer filter, legacy (`["==", "class", "x"]`) or expression form.
         */
        internal fun compileFilter(json: JsonElement): Expression {
            return if (ExpressionCompiler.isExpressionFilter(json)) {
                ExpressionCompiler.compile(json)
            } else {
                ExpressionCompiler.compileLegacyFilter(json)
            }
        }
    }
}

class UnsupportedExpressionException(message: String) : IllegalArgumentException(message)

/**
 * An ARGB colour, not premultiplied.
 */
@JvmInline
value class Color(val argb: Int) {

    val alpha: Int
        get() {
            return argb ushr 24
        }

    /**
     * This colour with its alpha multiplied by `opacity`.
     */
    fun withOpacity(opacity: Double): Color {
        val a = (alpha * opacity.coerceIn(0.0, 1.0) + 0.5).toInt()
        return Color((a shl 24) or (argb and 0xffffff))
    }
}

internal class Constant(private val value: Any?) : Expression() {

    override fun eval(ctx: EvalContext): Any? {
        return value
    }
}

private class Op(
    private val args: Array<Expression>,
    private val readsFeature: Boolean,
    private val readsZoom: Boolean,
    private val fn: (EvalContext, Array<Expression>) -> Any?,
) : Expression() {

    override val isFeatureConstant: Boolean = !readsFeature && args.all { it.isFeatureConstant }

    override val isZoomConstant: Boolean = !readsZoom && args.all { it.isZoomConstant }

    override fun eval(ctx: EvalContext): Any? {
        return fn(ctx, args)
    }
}

internal object ExpressionCompiler {

    fun compile(json: JsonElement): Expression {
        return when (json) {
            is JsonArray -> compileArray(json)
            is JsonObject -> throw UnsupportedExpressionException("function object ${json.keys}")
            else -> Constant(toValue(json))
        }
    }

    fun toValue(json: JsonElement): Any? {
        return when (json) {
            JsonNull -> null
            is JsonPrimitive -> when {
                json.isString -> json.content
                else -> json.booleanOrNull ?: json.doubleOrNull
            }
            is JsonArray -> json.map { toValue(it) }
            is JsonObject -> json.mapValues { toValue(it.value) }
        }
    }

    // MapLibre's isExpressionFilter
    fun isExpressionFilter(json: JsonElement): Boolean {
        if (json !is JsonArray
            || json.isEmpty()
        ) {
            return json is JsonPrimitive && json.booleanOrNull != null
        }
        val op = (json[0] as? JsonPrimitive)?.content ?: return false
        return when (op) {
            "has" -> json.size >= 2
                && !json[1].isString("\$id")
                && !json[1].isString("\$type")
            "in" -> json.size >= 3 && (!json[1].isAnyString() || json[2] is JsonArray)
            "!in", "!has", "none" -> false
            "==", "!=", ">", ">=", "<", "<=" -> json.size != 3
                || json[1] is JsonArray
                || json[2] is JsonArray
            "any", "all" -> json.drop(1).all { it !is JsonArray || isExpressionFilter(it) }
            else -> true
        }
    }

    fun compileLegacyFilter(json: JsonElement): Expression {
        if (json !is JsonArray
            || json.isEmpty()
        ) {
            return Constant(true)
        }
        val name = (json[0] as JsonPrimitive).content
        val key = if (json.size > 1) (json[1] as? JsonPrimitive)?.content ?: "" else ""
        val values = json.drop(2).map { toValue(it) }
        require(values.isNotEmpty()
            || name in setOf("all", "any", "none", "has", "!has", "in", "!in")
        ) {
            "legacy filter $name without a value"
        }
        val read = legacyGetter(key)
        return when (name) {
            "all" -> op(json.drop(1).map { compileLegacyFilter(it) }) { ctx, a ->
                a.all { it.eval(ctx) == true }
            }
            "any" -> op(json.drop(1).map { compileLegacyFilter(it) }) { ctx, a ->
                a.any { it.eval(ctx) == true }
            }
            "none" -> op(json.drop(1).map { compileLegacyFilter(it) }) { ctx, a ->
                a.none { it.eval(ctx) == true }
            }
            "has" -> feature { ctx -> read(ctx) != null }
            "!has" -> feature { ctx -> read(ctx) == null }
            "in" -> feature { ctx -> values.any { looseEquals(read(ctx), it) } }
            "!in" -> feature { ctx -> values.none { looseEquals(read(ctx), it) } }
            "==" -> feature { ctx -> looseEquals(read(ctx), values[0]) }
            "!=" -> feature { ctx -> !looseEquals(read(ctx), values[0]) }
            "<", "<=", ">", ">=" -> feature { ctx -> compareValues(name, read(ctx), values[0]) }
            else -> throw UnsupportedExpressionException("legacy filter $name")
        }
    }

    private fun legacyGetter(key: String): (EvalContext) -> Any? {
        return when (key) {
            "\$type" -> { ctx -> ctx.feature?.type?.let { geometryTypeName(it) } }
            "\$id" -> { ctx -> ctx.feature?.id?.toDouble() }
            else -> { ctx -> ctx.feature?.property(key) }
        }
    }

    private fun feature(fn: (EvalContext) -> Any?): Expression {
        return Op(emptyArray(), readsFeature = true, readsZoom = false) { ctx, _ -> fn(ctx) }
    }

    private fun op(args: List<Expression>, fn: (EvalContext, Array<Expression>) -> Any?): Expression {
        return Op(args.toTypedArray(), readsFeature = false, readsZoom = false, fn)
    }

    private fun compileArray(json: JsonArray): Expression {
        if (json.isEmpty()
            || !json[0].isAnyString()
            || (json[0] as JsonPrimitive).content !in OPERATORS
        ) {
            // an array constant, such as a dasharray or a `text-font` stack
            return Constant(toValue(json))
        }
        val name = (json[0] as JsonPrimitive).content
        val raw = json.drop(1)
        return when (name) {
            "literal" -> Constant(toValue(raw[0]))
            "get" -> {
                require(raw.size == 1) {
                    "get with an object argument"
                }
                val key = (raw[0] as JsonPrimitive).content
                if (key == "name") {
                    feature { ctx ->
                        val feature = ctx.feature
                        ctx.localNames.firstNotNullOfOrNull { feature?.property(it) } ?: feature?.property(key)
                    }
                } else {
                    feature { ctx -> ctx.feature?.property(key) }
                }
            }
            "has" -> {
                val key = (raw[0] as JsonPrimitive).content
                feature { ctx -> ctx.feature?.property(key) != null }
            }
            "geometry-type" -> feature { ctx -> ctx.feature?.type?.let { geometryTypeName(it) } }
            "id" -> feature { ctx -> ctx.feature?.id?.toDouble() }
            "zoom" -> Op(emptyArray(), readsFeature = false, readsZoom = true) { ctx, _ -> ctx.zoom }
            "let" -> {
                // name, value pairs and a body
                require(raw.size >= 3
                    && raw.size % 2 == 1
                ) {
                    "let with ${raw.size} arguments"
                }
                val names = raw.dropLast(1)
                    .filterIndexed { i, _ -> i % 2 == 0 }
                    .map { (it as JsonPrimitive).content }
                val values = raw.dropLast(1).filterIndexed { i, _ -> i % 2 == 1 }.map { compile(it) }
                op(values + compile(raw.last())) { ctx, a ->
                    val saved = names.map { ctx.bindings[it] }
                    for (i in names.indices) {
                        ctx.bindings[names[i]] = a[i].eval(ctx)
                    }
                    val result = a.last().eval(ctx)
                    for (i in names.indices) {
                        ctx.bindings[names[i]] = saved[i]
                    }
                    result
                }
            }
            "var" -> {
                val key = (raw[0] as JsonPrimitive).content
                // a binding may hold feature or zoom data
                Op(emptyArray(), readsFeature = true, readsZoom = true) { ctx, _ -> ctx.bindings[key] }
            }
            "interpolate" -> compileInterpolate(raw)
            "step" -> compileStep(raw)
            else -> {
                val args = raw.map { compile(it) }
                // a short call would throw at eval, aborting the tile; fail into Style.problems
                require(args.size >= (MIN_ARGS[name] ?: 0)) {
                    "$name with ${args.size} arguments"
                }
                compileCall(name, args)
            }
        }
    }

    private fun compileCall(name: String, args: List<Expression>): Expression {
        return when (name) {
            "all" -> op(args) { ctx, a -> a.all { it.eval(ctx) == true } }
            "any" -> op(args) { ctx, a -> a.any { it.eval(ctx) == true } }
            "!" -> op(args) { ctx, a -> a[0].eval(ctx) != true }
            "==" -> op(args) { ctx, a -> looseEquals(a[0].eval(ctx), a[1].eval(ctx)) }
            "!=" -> op(args) { ctx, a -> !looseEquals(a[0].eval(ctx), a[1].eval(ctx)) }
            "<", "<=", ">", ">=" -> op(args) { ctx, a -> compareValues(name, a[0].eval(ctx), a[1].eval(ctx)) }
            "in" -> op(args) { ctx, a ->
                val needle = a[0].eval(ctx)
                when (val haystack = a[1].eval(ctx)) {
                    is List<*> -> haystack.any { looseEquals(needle, it) }
                    is String -> needle != null && haystack.contains(toText(needle))
                    else -> false
                }
            }
            "index-of" -> op(args) { ctx, a ->
                val needle = a[0].eval(ctx)
                when (val haystack = a[1].eval(ctx)) {
                    is List<*> -> haystack.indexOfFirst { looseEquals(needle, it) }.toDouble()
                    is String -> haystack.indexOf(toText(needle)).toDouble()
                    else -> -1.0
                }
            }
            "slice" -> op(args) { ctx, a ->
                val start = (a[1].eval(ctx) as? Double)?.toInt() ?: 0
                val end = if (a.size > 2) (a[2].eval(ctx) as? Double)?.toInt() else null
                when (val v = a[0].eval(ctx)) {
                    is String -> {
                        val from = start.coerceIn(0, v.length)
                        v.substring(from, (end ?: v.length).coerceIn(from, v.length))
                    }
                    is List<*> -> {
                        val from = start.coerceIn(0, v.size)
                        v.subList(from, (end ?: v.size).coerceIn(from, v.size))
                    }
                    else -> null
                }
            }
            "case" -> op(args) { ctx, a ->
                var i = 0
                while (i + 1 < a.size) {
                    if (a[i].eval(ctx) == true) {
                        return@op a[i + 1].eval(ctx)
                    }
                    i += 2
                }
                a.last().eval(ctx)
            }
            "match" -> compileMatch(args)
            "coalesce" -> op(args) { ctx, a ->
                for (e in a) {
                    val v = e.eval(ctx)
                    if (v != null) {
                        return@op v
                    }
                }
                null
            }
            "concat" -> op(args) { ctx, a -> a.joinToString("") { toText(it.eval(ctx)) } }
            "upcase" -> op(args) { ctx, a -> toText(a[0].eval(ctx)).uppercase() }
            "downcase" -> op(args) { ctx, a -> toText(a[0].eval(ctx)).lowercase() }
            "to-string" -> op(args) { ctx, a -> toText(a[0].eval(ctx)) }
            "to-number" -> op(args) { ctx, a ->
                for (e in a) {
                    val n = toNumberOrNull(e.eval(ctx))
                    if (n != null) {
                        return@op n
                    }
                }
                0.0
            }
            "to-boolean" -> op(args) { ctx, a ->
                when (val v = a[0].eval(ctx)) {
                    null, false, 0.0, "" -> false
                    is Double -> !v.isNaN()
                    else -> true
                }
            }
            "to-color" -> op(args) { ctx, a -> toColor(a[0].eval(ctx)) }
            "string", "number", "boolean" -> op(args) { ctx, a -> a[0].eval(ctx) }
            "length" -> op(args) { ctx, a ->
                when (val v = a[0].eval(ctx)) {
                    is String -> v.length.toDouble()
                    is List<*> -> v.size.toDouble()
                    else -> 0.0
                }
            }
            "+" -> op(args) { ctx, a -> a.sumOf { num(it.eval(ctx)) } }
            "*" -> op(args) { ctx, a -> a.fold(1.0) { acc, e -> acc * num(e.eval(ctx)) } }
            "-" -> op(args) { ctx, a ->
                if (a.size == 1) -num(a[0].eval(ctx)) else num(a[0].eval(ctx)) - num(a[1].eval(ctx))
            }
            "/" -> op(args) { ctx, a -> num(a[0].eval(ctx)) / num(a[1].eval(ctx)) }
            "%" -> op(args) { ctx, a -> num(a[0].eval(ctx)) % num(a[1].eval(ctx)) }
            "^" -> op(args) { ctx, a -> num(a[0].eval(ctx)).pow(num(a[1].eval(ctx))) }
            "min" -> op(args) { ctx, a -> a.minOf { num(it.eval(ctx)) } }
            "max" -> op(args) { ctx, a -> a.maxOf { num(it.eval(ctx)) } }
            "ceil" -> op(args) { ctx, a -> ceil(num(a[0].eval(ctx))) }
            "floor" -> op(args) { ctx, a -> floor(num(a[0].eval(ctx))) }
            "round" -> op(args) { ctx, a -> Math.round(num(a[0].eval(ctx))).toDouble() }
            "abs" -> op(args) { ctx, a -> kotlin.math.abs(num(a[0].eval(ctx))) }
            else -> throw UnsupportedExpressionException(name)
        }
    }

    private fun compileMatch(args: List<Expression>): Expression {
        // labels are constants: a value or an array of values
        val input = args[0]
        val labels = ArrayList<List<Any?>>()
        val outputs = ArrayList<Expression>()
        var i = 1
        while (i + 1 < args.size) {
            val label = (args[i] as? Constant)?.eval(EvalContext())
            labels.add(if (label is List<*>) label else listOf(label))
            outputs.add(args[i + 1])
            i += 2
        }
        val fallback = args.last()
        return op(listOf(input) + outputs + fallback) { ctx, _ ->
            val v = input.eval(ctx)
            for (j in labels.indices) {
                if (labels[j].any { looseEquals(v, it) }) {
                    return@op outputs[j].eval(ctx)
                }
            }
            fallback.eval(ctx)
        }
    }

    private fun compileInterpolate(raw: List<JsonElement>): Expression {
        val type = raw[0] as JsonArray
        val base = when ((type[0] as JsonPrimitive).content) {
            "linear" -> 1.0
            "exponential" -> (type[1] as JsonPrimitive).doubleOrNull ?: 1.0
            // approximated as linear
            "cubic-bezier" -> 1.0
            else -> throw UnsupportedExpressionException("interpolate ${type[0]}")
        }
        val input = compile(raw[1])
        val stops = DoubleArray((raw.size - 2) / 2) {
            (raw[2 + it * 2] as JsonPrimitive).doubleOrNull ?: 0.0
        }
        require(stops.isNotEmpty()) {
            "interpolate without stops"
        }
        val outputs = List(stops.size) { compile(raw[3 + it * 2]) }
        return op(listOf(input) + outputs) { ctx, _ ->
            val x = num(input.eval(ctx))
            if (x.isNaN()) {
                return@op outputs[0].eval(ctx)
            }
            if (x <= stops[0]) {
                return@op outputs[0].eval(ctx)
            }
            val last = stops.size - 1
            if (x >= stops[last]) {
                return@op outputs[last].eval(ctx)
            }
            var j = 0
            while (x >= stops[j + 1]) {
                j++
            }
            val t = interpolationFactor(x, stops[j], stops[j + 1], base)
            interpolateValue(outputs[j].eval(ctx), outputs[j + 1].eval(ctx), t)
        }
    }

    private fun compileStep(raw: List<JsonElement>): Expression {
        val input = compile(raw[0])
        val first = compile(raw[1])
        val stops = DoubleArray((raw.size - 2) / 2) {
            (raw[2 + it * 2] as JsonPrimitive).doubleOrNull ?: 0.0
        }
        val outputs = List(stops.size) { compile(raw[3 + it * 2]) }
        return op(listOf(input, first) + outputs) { ctx, _ ->
            val x = num(input.eval(ctx))
            var result = first
            for (j in stops.indices) {
                if (x >= stops[j]) {
                    result = outputs[j]
                } else {
                    break
                }
            }
            result.eval(ctx)
        }
    }

    private fun interpolationFactor(x: Double, lower: Double, upper: Double, base: Double): Double {
        val range = upper - lower
        if (range == 0.0) {
            return 0.0
        }
        val progress = x - lower
        return if (base == 1.0) {
            progress / range
        } else {
            (base.pow(progress) - 1) / (base.pow(range) - 1)
        }
    }

    private fun interpolateValue(a: Any?, b: Any?, t: Double): Any? {
        if (a is Double
            && b is Double
        ) {
            return a + (b - a) * t
        }
        if (a is List<*>
            && b is List<*>
        ) {
            return a.indices.map { interpolateValue(a[it], b.getOrNull(it), t) }
        }
        val ca = toColor(a)
        val cb = toColor(b)
        if (ca != null
            && cb != null
        ) {
            return Colors.interpolate(ca, cb, t)
        }
        return if (t < 0.5) a else b
    }

    fun geometryTypeName(type: GeometryType): String {
        return when (type) {
            GeometryType.POINT -> "Point"
            GeometryType.LINESTRING -> "LineString"
            GeometryType.POLYGON -> "Polygon"
            GeometryType.UNKNOWN -> "Unknown"
        }
    }

    fun looseEquals(a: Any?, b: Any?): Boolean {
        return a == b
    }

    private fun compareValues(op: String, a: Any?, b: Any?): Boolean {
        val c = when {
            a is Double && b is Double -> a.compareTo(b)
            a is String && b is String -> a.compareTo(b)
            else -> return false
        }
        return when (op) {
            "<" -> c < 0
            "<=" -> c <= 0
            ">" -> c > 0
            else -> c >= 0
        }
    }

    fun num(v: Any?): Double {
        return v as? Double ?: 0.0
    }

    private fun toNumberOrNull(v: Any?): Double? {
        return when (v) {
            is Double -> v
            is String -> v.toDoubleOrNull()
            is Boolean -> if (v) 1.0 else 0.0
            else -> null
        }
    }

    fun toText(v: Any?): String {
        return when (v) {
            null -> ""
            is Double -> {
                val whole = v == floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15
                if (whole) v.toLong().toString() else v.toString()
            }
            is Color -> Colors.format(v)
            else -> v.toString()
        }
    }

    fun toColor(v: Any?): Color? {
        return when (v) {
            is Color -> v
            is String -> Colors.parse(v)
            else -> null
        }
    }

    private val MIN_ARGS = mapOf(
        "!" to 1, "==" to 2, "!=" to 2, "<" to 2, "<=" to 2, ">" to 2, ">=" to 2, "in" to 2,
        "index-of" to 2, "slice" to 2, "case" to 1, "match" to 2, "coalesce" to 1, "upcase" to 1,
        "downcase" to 1, "to-string" to 1, "to-number" to 1, "to-boolean" to 1, "to-color" to 1,
        "string" to 1, "number" to 1, "boolean" to 1, "length" to 1, "-" to 1, "/" to 2, "%" to 2,
        "^" to 2, "min" to 1, "max" to 1, "ceil" to 1, "floor" to 1, "round" to 1, "abs" to 1,
    )

    // every operator of the style spec; an array starting with one of them is an expression
    private val OPERATORS = setOf(
        "let", "var", "literal", "array", "at", "in", "index-of", "slice", "global-state", "case",
        "match", "coalesce", "step", "interpolate", "interpolate-hcl", "interpolate-lab", "ln2",
        "pi", "e", "typeof", "string", "number", "boolean", "object", "collator", "format",
        "image", "number-format", "to-string", "to-number", "to-boolean", "to-rgba", "to-color",
        "rgb", "rgba", "get", "has", "length", "properties", "feature-state", "geometry-type",
        "id", "zoom", "heatmap-density", "line-progress", "accumulated", "elevation", "+", "*",
        "-", "/", "%", "^", "sqrt", "log10", "ln", "log2", "sin", "cos", "tan", "asin", "acos",
        "atan", "min", "max", "round", "abs", "ceil", "floor", "distance", "within", "==", "!=",
        ">", "<", ">=", "<=", "all", "any", "!", "is-supported-script", "upcase", "downcase",
        "concat", "resolved-locale", "split", "join",
    )

    private fun JsonElement.isAnyString(): Boolean {
        return this is JsonPrimitive && isString
    }

    private fun JsonElement.isString(value: String): Boolean {
        return this is JsonPrimitive && isString && content == value
    }
}
