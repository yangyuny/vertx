package com.yangyun.web;

public class SplitTable {

    public static void main(String[] args) {
        //假设数据库有count_db=256个，每个库中有count_table=1024个表，用户的user_id=262145
        Integer count_db = 256;
        Integer count_table = 1024;
        Integer user_id = 262145;
        int middle = user_id % (count_db * count_table);
        int cdb = middle / count_table;
        int ctable = middle % count_table;
        System.out.println("对于user_id = 262145，将被路由到第" + cdb + "个数据库的第" + ctable + "个表中");
    }
}