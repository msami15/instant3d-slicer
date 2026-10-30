package com.instant3d.slicer.profile

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "material_profiles")
data class MaterialProfile(
    @PrimaryKey val id: String,
    val name: String,
    val densityGPerCm3: Double,
    val filamentDiameterMm: Double = 1.75,
    val spoolWeightGrams: Double,
    val spoolPricePkr: Double,
    val isBuiltIn: Boolean,
)

@Entity(tableName = "printer_profiles")
data class PrinterProfile(
    @PrimaryKey val id: String,
    val name: String,
    val buildXmm: Double,
    val buildYmm: Double,
    val buildZmm: Double,
    val nozzleDiameterMm: Double,
    val isBuiltIn: Boolean = true,
)

@Entity(tableName = "profile_preferences")
data class ProfilePreferences(
    @PrimaryKey val id: Int = 1,
    val defaultMaterialId: String = "pla",
    val defaultPrinterId: String = "bambu-a1",
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM material_profiles ORDER BY isBuiltIn DESC, name COLLATE NOCASE")
    fun observeMaterials(): Flow<List<MaterialProfile>>

    @Query("SELECT * FROM printer_profiles ORDER BY name COLLATE NOCASE")
    fun observePrinters(): Flow<List<PrinterProfile>>

    @Query("SELECT * FROM profile_preferences WHERE id = 1")
    fun observePreferences(): Flow<ProfilePreferences?>

    @Query("SELECT * FROM profile_preferences WHERE id = 1")
    suspend fun getPreferences(): ProfilePreferences?

    @Query("SELECT * FROM material_profiles WHERE id = :id")
    suspend fun getMaterial(id: String): MaterialProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMaterial(profile: MaterialProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePrinter(profile: PrinterProfile)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMaterials(profiles: List<MaterialProfile>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrinters(profiles: List<PrinterProfile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(preferences: ProfilePreferences)

    @Query("DELETE FROM material_profiles WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomMaterial(id: String)
}

@Database(
    entities = [MaterialProfile::class, PrinterProfile::class, ProfilePreferences::class],
    version = 1,
    exportSchema = false,
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile private var instance: ProfileDatabase? = null

        fun get(context: Context): ProfileDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ProfileDatabase::class.java,
                "profiles.db",
            ).build().also { instance = it }
        }
    }
}

class ProfileRepository(private val dao: ProfileDao) {
    val materials: Flow<List<MaterialProfile>> = dao.observeMaterials()
    val printers: Flow<List<PrinterProfile>> = dao.observePrinters()
    val preferences: Flow<ProfilePreferences?> = dao.observePreferences()

    suspend fun ensureDefaults() {
        dao.insertMaterials(DEFAULT_MATERIALS)
        dao.insertPrinters(DEFAULT_PRINTERS)
        if (dao.getPreferences() == null) dao.savePreferences(ProfilePreferences())
    }

    suspend fun saveMaterial(profile: MaterialProfile) {
        require(profile.name.isNotBlank()) { "Enter a material name." }
        require(profile.densityGPerCm3 > 0.0) { "Density must be greater than zero." }
        require(profile.filamentDiameterMm > 0.0) { "Filament diameter must be greater than zero." }
        require(profile.spoolWeightGrams > 0.0) { "Spool weight must be greater than zero." }
        require(profile.spoolPricePkr >= 0.0) { "Price cannot be negative." }
        dao.saveMaterial(profile.copy(name = profile.name.trim()))
    }

    suspend fun savePrinter(profile: PrinterProfile) {
        require(profile.name.isNotBlank()) { "Enter a printer name." }
        require(profile.buildXmm > 0.0 && profile.buildYmm > 0.0 && profile.buildZmm > 0.0) {
            "Build volume dimensions must be greater than zero."
        }
        require(profile.nozzleDiameterMm > 0.0) { "Nozzle diameter must be greater than zero." }
        dao.savePrinter(profile.copy(name = profile.name.trim()))
    }

    suspend fun deleteCustomMaterial(id: String) {
        dao.deleteCustomMaterial(id)
        val preferences = dao.getPreferences() ?: ProfilePreferences()
        if (preferences.defaultMaterialId == id) {
            dao.savePreferences(preferences.copy(defaultMaterialId = "pla"))
        }
    }

    suspend fun setDefaultMaterial(id: String) {
        require(dao.getMaterial(id) != null) { "Material profile no longer exists." }
        dao.savePreferences((dao.getPreferences() ?: ProfilePreferences()).copy(defaultMaterialId = id))
    }

    suspend fun setDefaultPrinter(id: String) {
        require(id in DEFAULT_PRINTERS.map { it.id } || dao.observePrinters() != null)
        dao.savePreferences((dao.getPreferences() ?: ProfilePreferences()).copy(defaultPrinterId = id))
    }

    companion object {
        val DEFAULT_MATERIALS = listOf(
            MaterialProfile("pla", "PLA", 1.24, spoolWeightGrams = 1000.0, spoolPricePkr = 0.0, isBuiltIn = true),
            MaterialProfile("petg", "PETG", 1.27, spoolWeightGrams = 1000.0, spoolPricePkr = 0.0, isBuiltIn = true),
            MaterialProfile("abs", "ABS", 1.04, spoolWeightGrams = 1000.0, spoolPricePkr = 0.0, isBuiltIn = true),
            MaterialProfile("tpu", "TPU", 1.21, spoolWeightGrams = 1000.0, spoolPricePkr = 0.0, isBuiltIn = true),
        )

        // Manufacturer stated build volumes and included nozzle defaults; users can adjust these locally.
        val DEFAULT_PRINTERS = listOf(
            PrinterProfile("bambu-a1", "Bambu Lab A1", 256.0, 256.0, 256.0, 0.4),
            PrinterProfile("bambu-a1-mini", "Bambu Lab A1 Mini", 180.0, 180.0, 180.0, 0.4),
            PrinterProfile("bambu-p1p", "Bambu Lab P1P", 256.0, 256.0, 256.0, 0.4),
            PrinterProfile("bambu-p1s", "Bambu Lab P1S", 256.0, 256.0, 256.0, 0.4),
            PrinterProfile("bambu-x1-carbon", "Bambu Lab X1 Carbon", 256.0, 256.0, 256.0, 0.4),
        )
    }
}
