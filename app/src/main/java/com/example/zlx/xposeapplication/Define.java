package com.example.zlx.xposeapplication;

public class Define {
    // note: status
    public static int MESSAGE_STATUS_SEND_SUCCESS = 2;  // 成功发送消息给对方

    // note: type
    public static int MESSAGE_TYPE_CARD_MSG = 49;  // 卡片类型

    /** 表结构:
     * CREATE TABLE appattach (  appId TEXT,  sdkVer LONG,  mediaSvrId TEXT,  mediaId TEXT,  clientAppDataId TEXT,  type LONG,  totalLen LONG,  offset LONG,  status LONG,  isUpload INTEGER,  createTime LONG,  lastModifyTime LONG,  fileFullPath TEXT,  msgInfoId LONG,  netTimes LONG,  isUseCdn INTEGER);
     *
     * CREATE TABLE message ( msgId INTEGER PRIMARY KEY, msgSvrId INTEGER , type INT, status INT, isSend INT, isShowTimer INTEGER, createTime INTEGER, talker TEXT, content TEXT, imgPath TEXT, reserved TEXT, lvbuffer BLOB, transContent TEXT,transBrandWording TEXT ,talkerId INTEGER, bizClientMsgId TEXT, bizChatId INTEGER DEFAULT -1, bizChatUserId TEXT, msgSeq INTEGER, flag INT);     *
     *
     * CREATE TABLE AppMessage (  msgId LONG default '0'  PRIMARY KEY ,  xml TEXT,  appId TEXT,  title TEXT,  description TEXT,  source TEXT,  type INTEGER);
     */
}
