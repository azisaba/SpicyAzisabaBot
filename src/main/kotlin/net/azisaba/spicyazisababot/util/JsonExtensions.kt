package net.azisaba.spicyazisababot.util

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

val JsonElement.string: String? get() = this.jsonPrimitive.contentOrNull
val JsonElement.int: Int? get() = this.jsonPrimitive.intOrNull
val JsonElement.boolean: Boolean? get() = this.jsonPrimitive.booleanOrNull
val JsonElement.float: Float? get() = this.jsonPrimitive.floatOrNull
val JsonElement.double: Double? get() = this.jsonPrimitive.doubleOrNull
val JsonElement.long: Long? get() = this.jsonPrimitive.longOrNull
fun JsonObject.getLong(key: String): Long? = getPrimitive(key)?.longOrNull
fun JsonObject.getFloat(key: String): Float? = getPrimitive(key)?.floatOrNull
fun JsonObject.getDouble(key: String): Double? = getPrimitive(key)?.doubleOrNull
fun JsonObject.getBoolean(key: String): Boolean? = getPrimitive(key)?.booleanOrNull
fun JsonObject.getInt(key: String): Int? = getPrimitive(key)?.intOrNull
fun JsonObject.getString(key: String): String? = getPrimitive(key)?.contentOrNull
fun JsonObject.getArray(key: String): List<JsonElement>? = this[key]?.jsonArray
fun JsonObject.getObjectArray(key: String): List<JsonObject>? = getArray(key)?.map { it.jsonObject }
fun JsonObject.getStringArray(key: String): List<String?>? = getArray(key)?.map { it.string }
fun JsonObject.getIntArray(key: String): List<Int?>? = getArray(key)?.map { it.int }
fun JsonObject.getDoubleArray(key: String): List<Double?>? = getArray(key)?.map { it.double }
fun JsonObject.getFloatArray(key: String): List<Float?>? = getArray(key)?.map { it.float }
fun JsonObject.getLongArray(key: String): List<Long?>? = getArray(key)?.map { it.long }
fun JsonObject.getBooleanArray(key: String): List<Boolean?>? = getArray(key)?.map { it.boolean }
fun JsonObject.getObject(key: String): JsonObject? = this[key]?.jsonObject
fun JsonObject.getPrimitive(key: String): JsonPrimitive? = this[key]?.jsonPrimitive
