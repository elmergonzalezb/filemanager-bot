package me.shib.java.app.telegram.bot.filemanager.main;

import me.shib.java.app.telegram.bot.filemanager.navigator.KeyBoardAndResponseText;
import me.shib.java.app.telegram.bot.filemanager.navigator.UserBase;
import me.shib.java.app.telegram.bot.filemanager.navigator.UserDir;
import me.shib.java.lib.common.utils.LocalFileCache;
import me.shib.java.lib.jbots.JBot;
import me.shib.java.lib.jbots.JBotConfig;
import me.shib.java.lib.jbots.MessageHandler;
import me.shib.java.lib.jtelebot.service.TelegramBot;
import me.shib.java.lib.jtelebot.types.*;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class FileManagerBot extends JBot {

    private static final long maxFileSize = 50000000;

    private static Map<Long, LocalFileCache> localFileCacheMap;

    private ChatActionHandler cah;
    private LocalFileCache localCache;
    private UserBase userBase;

    public FileManagerBot(JBotConfig config) {
        super(config);
        this.localCache = getLocalFileCache(bot.getIdentity().getId());
        this.userBase = UserBase.getInstance(config, bot.getIdentity().getId());
    }

    private static synchronized LocalFileCache getLocalFileCache(long botId) {
        if (localFileCacheMap == null) {
            localFileCacheMap = new HashMap<>();
        }
        LocalFileCache cache = localFileCacheMap.get(botId);
        if (cache == null) {
            cache = new LocalFileCache(8640000, "filemanager-bot-cache-" + botId, true);
            localFileCacheMap.put(botId, cache);
        }
        return cache;
    }

    @Override
    public MessageHandler onMessage(Message message) {
        return new MessageHandler(message) {
            @Override
            public boolean onCommandFromAdmin(String command) {
                return false;
            }

            @Override
            public boolean onCommandFromUser(String command) {
                return false;
            }

            @Override
            public boolean onMessageFromAdmin() {
                return onMessageFromUser();
            }

            @Override
            public boolean onMessageFromUser() {
                try {
                    if (message.getText() == null) {
                        bot.sendMessage(new ChatId(message.getChat().getId()), "Please input a text");
                    } else {
                        UserDir ud = userBase.getUserDir(message.getChat().getId());
                        ud.navigate(message.getText());
                        KeyBoardAndResponseText kbt = ud.getCurrentResponse();
                        String[] fileNameList = kbt.getFileList();
                        String[] navigationButtons;
                        if (ud.isShowNextButton() && ud.isShowPrevButton()) {
                            navigationButtons = new String[4];
                            navigationButtons[2] = "/previous";
                            navigationButtons[3] = "/next";
                        } else if (ud.isShowNextButton() || ud.isShowPrevButton()) {
                            navigationButtons = new String[3];
                            if (ud.isShowPrevButton()) {
                                navigationButtons[2] = "/previous";
                            } else {
                                navigationButtons[2] = "/next";
                            }
                        } else {
                            navigationButtons = new String[2];
                        }
                        navigationButtons[0] = "/home";
                        navigationButtons[1] = "/back";
                        String[][] keyboard = new String[fileNameList.length + 1][1];
                        keyboard[0] = navigationButtons;
                        for (int i = 0; i < fileNameList.length; i++) {
                            keyboard[i + 1][0] = fileNameList[i];
                        }
                        File fileToSend = ud.getFile();
                        String consumableSuggestionMessage = ud.getConsumableSearchSuggestion();
                        if (fileToSend != null) {
                            if (fileToSend.isDirectory()) {
                                File[] filesToSend = userBase.getFilesInDirectory(fileToSend);
                                for (File f : filesToSend) {
                                    if (!f.isDirectory()) {
                                        sendFileToUser(bot, ud, f);
                                    }
                                }
                            } else {
                                sendFileToUser(bot, ud, fileToSend);
                            }
                        }
                        if (consumableSuggestionMessage != null) {
                            bot.sendMessage(new ChatId(ud.getUserId()), consumableSuggestionMessage, false, null, true);
                        } else {
                            bot.sendMessage(new ChatId(ud.getUserId()), kbt.getResponse(), false, null, true, 0, new ReplyKeyboardMarkup(keyboard, true, true));
                        }
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }

    private String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private String getFileInfo(File file) {
        return ("Name: " + file.getName() + "\n") + "Size: " + humanReadableByteCount(file.length(), false) + "\n" + "Last Modified: " + new Date(file.lastModified());
    }

    private void startChatAction(TelegramBot tBotService, ChatId chatId, ChatAction chatAction) {
        cah = new ChatActionHandler(tBotService, chatId, chatAction);
        cah.start();
    }

    private void endChatAction() {
        cah.endAction();
    }

    private String getFileIdFromMessage(Message message) {
        if (message == null) {
            return null;
        }
        if (message.getDocument() != null) {
            return message.getDocument().getFile_id();
        } else if (message.getPhoto() != null) {
            return message.getPhoto()[message.getPhoto().length - 1].getFile_id();
        } else if (message.getVideo() != null) {
            return message.getVideo().getFile_id();
        } else if (message.getVoice() != null) {
            return message.getVoice().getFile_id();
        } else if (message.getAudio() != null) {
            return message.getAudio().getFile_id();
        }
        return null;
    }

    private void sendFileToUser(TelegramBot tBotService, UserDir ud, File fileToSend) throws IOException {
        if (fileToSend.length() > maxFileSize) {
            tBotService.sendMessage(new ChatId(ud.getUserId()), "The file you requested is larger in size than the permissible limit:\n" + getFileInfo(fileToSend), false, null, true);
        } else {
            startChatAction(tBotService, new ChatId(ud.getUserId()), ChatAction.upload_document);
            String fileId = localCache.getDataforKey(userBase.getHomeDir().getPath(), fileToSend.getAbsolutePath());
            if (fileId == null) {
                Message fileSentMessage = tBotService.sendDocument(new ChatId(ud.getUserId()), new TelegramFile(fileToSend));
                String sentFileId = getFileIdFromMessage(fileSentMessage);
                if (sentFileId != null) {
                    localCache.putDataForKey(userBase.getHomeDir().getAbsolutePath(), fileToSend.getPath(), sentFileId);
                }
            } else {
                tBotService.sendDocument(new ChatId(ud.getUserId()), new TelegramFile(fileId.trim()));
            }
            endChatAction();
        }
    }

    public Message onReceivingMessage(Message message) {
        Message returnMessage = null;
        try {
            if (message.getText() == null) {
                bot.sendMessage(new ChatId(message.getChat().getId()), "Please input a text");
            } else {
                UserDir ud = userBase.getUserDir(message.getChat().getId());
                ud.navigate(message.getText());
                KeyBoardAndResponseText kbt = ud.getCurrentResponse();
                String[] fileNameList = kbt.getFileList();
                String[] navigationButtons;
                if (ud.isShowNextButton() && ud.isShowPrevButton()) {
                    navigationButtons = new String[4];
                    navigationButtons[2] = "/previous";
                    navigationButtons[3] = "/next";
                } else if (ud.isShowNextButton() || ud.isShowPrevButton()) {
                    navigationButtons = new String[3];
                    if (ud.isShowPrevButton()) {
                        navigationButtons[2] = "/previous";
                    } else {
                        navigationButtons[2] = "/next";
                    }
                } else {
                    navigationButtons = new String[2];
                }
                navigationButtons[0] = "/home";
                navigationButtons[1] = "/back";
                String[][] keyboard = new String[fileNameList.length + 1][1];
                keyboard[0] = navigationButtons;
                for (int i = 0; i < fileNameList.length; i++) {
                    keyboard[i + 1][0] = fileNameList[i];
                }
                File fileToSend = ud.getFile();
                String consumableSuggestionMessage = ud.getConsumableSearchSuggestion();
                if (fileToSend != null) {
                    if (fileToSend.isDirectory()) {
                        File[] filesToSend = userBase.getFilesInDirectory(fileToSend);
                        for (File f : filesToSend) {
                            if (!f.isDirectory()) {
                                sendFileToUser(bot, ud, f);
                            }
                        }
                    } else {
                        sendFileToUser(bot, ud, fileToSend);
                    }
                }
                if (consumableSuggestionMessage != null) {
                    returnMessage = bot.sendMessage(new ChatId(ud.getUserId()), consumableSuggestionMessage, false, null, true);
                } else {
                    returnMessage = bot.sendMessage(new ChatId(ud.getUserId()), kbt.getResponse(), false, null, true, 0, new ReplyKeyboardMarkup(keyboard, true, true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return returnMessage;
    }

    @Override
    public boolean onInlineQuery(InlineQuery query) {
        return false;
    }

    @Override
    public boolean onChosenInlineResult(ChosenInlineResult chosenInlineResult) {
        return false;
    }

}
