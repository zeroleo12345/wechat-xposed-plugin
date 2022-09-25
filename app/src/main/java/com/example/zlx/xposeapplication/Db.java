package com.example.zlx.xposeapplication;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.elvishew.xlog.XLog;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

enum DbOperation {
    INSERT, UPDATE
}

public class Db {
    private Object db_handler = null;
    Db(Object _db){this.db_handler = _db;}

    boolean isLock() {
        return (boolean) getObjectField(this.db_handler, "mHasAttachedDbsLocked");
    }

    Cursor rawQuery(String sql, String[] args) {
        //return (Cursor) callMethod(this.db_handler, "a", sql, args, 0);
        return (Cursor) callMethod(this.db_handler, "rawQuery", sql, args);
    }

    long rawInsert(String table, String selection, ContentValues contentValues) {
        // 返回表记录id
        return (long) callMethod(this.db_handler, "insert", table, selection, contentValues);
    }

    int rawUpdate(String table, ContentValues contentValues, String selection, String[] args) {
        return (int) callMethod(this.db_handler, "update", table, contentValues, selection, args);
    }

    int rawDelete(String table, String selection, String[] args) {
        /** 返回删除的记录条数 */
        return (int) callMethod(this.db_handler, "delete", table, selection, args);
    }

    void execSQL(String sql) {
        callMethod(this.db_handler, "execSQL", sql);    // execSQL
    }

    ContentValues _foreach(Cursor cursor){
        ContentValues contentvalues = new ContentValues();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            switch (cursor.getType(i)){
                case Cursor.FIELD_TYPE_BLOB:
                    contentvalues.put(cursor.getColumnName(i), cursor.getBlob(i) );     // note: 外层使用 contentValues.getAsByteArray("roomdata"); 取出byte[]
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    contentvalues.put(cursor.getColumnName(i), cursor.getFloat(i) );
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    contentvalues.put(cursor.getColumnName(i), cursor.getInt(i) );
                    break;
                case Cursor.FIELD_TYPE_NULL:
                    contentvalues.put(cursor.getColumnName(i), "");
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    contentvalues.put(cursor.getColumnName(i), cursor.getString(i) );
                    break;
            }
        }
        return contentvalues;
    }

    /**
     * @param sql   注意 sql 不要使用大写! 会崩溃!!
     * @param args  不可以为 null 或者 new String[]{}. 会崩溃! 必须有参数, 如 new String[]{""}
     * note 推荐查询数据库方式
     ContentValues contentValues = WechatClass.EnMicroMsg.select( "select roomdata from chatroom where chatroomname=?", new String[]{room} );
     if( contentValues == null ){
     XLog.e("select error, contentValues is null");
     return;
     }
     byte[] roomdata = contentValues.getAsByteArray("roomdata");
     if( roomdata == null ){
     XLog.e("get_inviter error, roomdata is null");
     return;
     }
     */
    ContentValues select(String sql, String[] args) {
        Cursor cursor = this.rawQuery(sql, args);
        try {
            if (cursor == null) {
                XLog.e("cursor is null, sql:%s, args:%s", sql, args);
                return null;
            }
            if (!cursor.moveToFirst()) {
                XLog.e("moveToFirst fail, sql:%s, args:%s", sql, args);
                return null;
            }
            return this._foreach(cursor);
        } catch (Exception e) {
            XLog.e("select error. stack:%s", Log.getStackTraceString(e));
        } finally {
            if (cursor != null) cursor.close();
        }
        XLog.e("select no row, sql:%s, args:%s", sql, args);
        return null;
    }
}   /** Db 类结束 */
