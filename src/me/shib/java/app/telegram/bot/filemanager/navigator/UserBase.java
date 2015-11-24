package me.shib.java.app.telegram.bot.filemanager.navigator;

import java.util.ArrayList;

import me.shib.java.lib.telegram.bot.easybot.TBotConfig;

public class UserBase {
	
	private static ArrayList<UserDir> userDirList = new ArrayList<UserDir>();
	
	public static UserDir getUserDir(long userId, TBotConfig fileManagerConfig) {
		for(int i = 0; i < userDirList.size(); i++) {
			if(userDirList.get(i).getUserId() == userId) {
				return userDirList.get(i);
			}
		}
		UserDir newUserDir = new UserDir(userId);
		userDirList.add(newUserDir);
		return newUserDir;
	}
	
}