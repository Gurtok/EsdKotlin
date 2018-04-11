/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dungeons.ca.esdkotlin

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject

/**
 * A class to buffer generated data to a dataBase for later upload.
 * @author Gurtok.
 * @version First version of ESD dataBase helper.
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  companion object {
    /** Main database name  */
    private const val DATABASE_NAME = "dbStorage"
    /** Database version.  */
    private const val DATABASE_VERSION = 1
    /** Table name for database.  */
    private const val TABLE_NAME = "StorageTable"
    /** Json data column name.  */
    private const val dataColumn = "JSON"
    /** Used to keep track of the database population.  */
    var databaseCount = 0L
  }

  /** Used to keep track of the database row we are working on.  */
  private var deleteRowId = 0
  /** Used to keep track of supplied database entries in case of upload failure.  */
  private var deleteBulkCount = 0
  private var sqlWriteDatabase: SQLiteDatabase

  /**
   * Creates a new dataBase if required on initialization.
   */
  override fun onCreate(db: SQLiteDatabase){


  }
  init {

    val query = "CREATE TABLE IF NOT EXISTS $TABLE_NAME (ID INTEGER PRIMARY KEY, JSON TEXT);"
    sqlWriteDatabase = super.getWritableDatabase()
    sqlWriteDatabase.execSQL(query)
    databaseCount = DatabaseUtils.queryNumEntries(sqlWriteDatabase, DatabaseHelper.TABLE_NAME, null)
    Log.e("dbHelper", "Database Count = $databaseCount"  )
  }

  /** Get number of database entries. */
  fun databaseEntries(): Long {
    //Log.e( "dbHelper", "database population: " + DatabaseUtils.queryNumEntries(writableDatabase, DatabaseHelper.TABLE_NAME, null) )
    return databaseCount
  }

  /**
   * @param db - Existing dataBase.
   * @param oldVersion - Old version number ID.
   * @param newVersion - New version number ID.
   */
  override fun onUpgrade(db: SQLiteDatabase , oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
  }

  /**
   * Pack the json object into a content values object for shipping.
   * Insert completed json object into dataBase.
   * Key will autoIncrement.
   * Will also start a background thread to upload the database to Kibana.
   * @param jsonObject - Passed object to be inserted.
   */
  fun jsonToDatabase(jsonObject: JSONObject) {
    val values = ContentValues()
    values.put(dataColumn, jsonObject.toString())
    sqlWriteDatabase = writableDatabase
    val checkDB: Int = sqlWriteDatabase.insert(TABLE_NAME, null, values).toInt()
    if (checkDB == -1) {
      Log.e("Failed insert", "Failed insert database.")
    } else {
      databaseCount++
    }

  }

  /** Delete a list of rows from database. */
  fun deleteUploadedIndices() {
    for ( i in 0..(deleteBulkCount-1) ) {
      sqlWriteDatabase.execSQL("DELETE FROM " + TABLE_NAME + " WHERE ID = " + (deleteRowId + i))
  }
    databaseCount = DatabaseUtils.queryNumEntries(sqlWriteDatabase, DatabaseHelper.TABLE_NAME, null)
  }

  /**
   * @return - The current database population.
   */
  fun getDeleteCount(): Int {
    return deleteBulkCount
  }

  /**
   * Query the database for up to 100 rows. Concatenate using the supplied schema.
   * Return null if database is empty.
   */
  fun getBulkString( esIndex: String, esType: String): String {

    var bulkOutString = ""
    val separatorString = "{\"index\":{\"_index\":\"$esIndex\",\"_type\":\"$esType\"}}"
    val newLine = "\n"

    if( this.databaseEntries() > 1 ){

      val outCursor = sqlWriteDatabase.rawQuery("SELECT * FROM " + DatabaseHelper.TABLE_NAME + " ORDER BY ID ASC LIMIT 500", Array(0,{""} ))
      deleteBulkCount = outCursor.count
      outCursor.moveToFirst()
      deleteRowId = outCursor.getInt(0)
      do{
        bulkOutString = String.format("%s%s%s%s%s",bulkOutString, separatorString, newLine, outCursor.getString(1), newLine)
        outCursor.moveToNext()
      }while(!outCursor.isAfterLast)
      outCursor.close()
    }
    //Log.e("dbHelper", "Data string = $bulkOutString")
    return bulkOutString
  }


}