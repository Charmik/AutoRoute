package com.autoroute.telegram;

import com.autoroute.logistic.LatLon;
import com.autoroute.telegram.db.Database;
import com.autoroute.telegram.db.Row;
import com.autoroute.telegram.db.State;
import com.autoroute.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Bot extends TelegramLongPollingBot {

    private static final Logger LOGGER = LogManager.getLogger(Bot.class);
    private static final int SEND_RETRIES = 5;
    private static final String TELEGRAM_KEY;

    private static final String SEND_LOCATION_MESSAGE = """
        This is auto generator of cycling routes over sights.
        Please send your location in the attachment menu. You can find it on the left of the bottom, the same button for sending photos on your phone.
        You can choose any location where you want to start your ride.
        You can forward your old message with location if you want to reuse the old one.""";

    private static final String SEND_DISTANCE_MESSAGE = """
        We got your location. Now please provide minimum and maximum distance for your trip via space.
        Difference between them should be more than 10km. Minimum distance is 30km. 
        They can't be more than 350 (for now).
        Example: \"30 60\"""";

    private static final String WAITING_FOR_RESULT = """
        You sent all information, your routes are in progress. It will take a few minutes.
        """;

    private static final String START_COMMAND = "Please start Bot with /start command.";

    static {
        try {
            TELEGRAM_KEY = Files.readString(Paths.get("config").resolve("telegramKey.txt"));
        } catch (IOException e) {
            throw new IllegalStateException("couldn't read telegram key");
        }
    }

    private final Database db;
    ThreadLocal<Boolean> telegramSentMessage;

    public Bot(DefaultBotOptions options, Database db) {
        super(options);
        this.db = db;
        this.telegramSentMessage = new ThreadLocal<>();
    }

    @Override
    public String getBotUsername() {
        return "AutoRouteBot";
    }

    @Override
    public String getBotToken() {
        return TELEGRAM_KEY;
    }

    @Override
    public void onUpdateReceived(Update update) {
        this.telegramSentMessage.set(false);
        if (update.hasMessage()) {
            final long chatId = update.getMessage().getChatId();
            final long msgDate = update.getMessage().getDate();
            @Nullable final Row dbRow = db.getRow(chatId);

            LOGGER.info("got update with chat_id: {}, date: {}, row: {}", chatId, msgDate, dbRow);
            LOGGER.info("message: {}", update.getMessage().getText());

            if (dbRow == null) {
                processStartCommand(chatId, msgDate, update, null);
            } else {
                switch (dbRow.state()) {
                    case CREATED -> processLocationUpdate(dbRow, msgDate, update);
                    case SENT_LOCATION -> processDistanceUpdate(dbRow, msgDate, update);
                    case SENT_DISTANCE -> sendMessage(chatId, WAITING_FOR_RESULT);
                    case FAILED_TO_PROCESS -> {
                        processStartCommand(chatId, msgDate, update, dbRow);
                        processRepeatCommand(chatId, msgDate, update, dbRow);
                    }
                    case GOT_ALL_ROUTES -> {
                        processStartCommand(chatId, msgDate, update, dbRow);
                        processRepeatCommand(chatId, msgDate, update, dbRow);
                    }
                }
            }
            if (!this.telegramSentMessage.get()) {
                if (dbRow != null && dbRow.state() == State.GOT_ALL_ROUTES) {
                    sendMessage(chatId, "You need to start bot with /start command. ");
                } else {
                    sendMessage(chatId, START_COMMAND);
                }
            }
        }
    }

    private void processStartCommand(long chatId, long msgDate, Update update, Row oldRow) {
        if (update.getMessage().hasText() && "/start".equals(update.getMessage().getText())) {
            Row row = new Row(chatId, update.getMessage().getChat().getUserName(), msgDate, State.CREATED);
            if (oldRow == null) {
                LOGGER.info("insert new row on /start: {}", row);
                db.insertRow(row);
                sendMessage(chatId, SEND_LOCATION_MESSAGE);
                sendPhoto(chatId, Paths.get("config").resolve("attachment_img.jpg"));
                // TOOD: FAILED_TO_PROCESS if we got the same data - skip it.
            } else if (oldRow.state() == State.GOT_ALL_ROUTES || oldRow.state() == State.FAILED_TO_PROCESS) {
                LOGGER.info("update row on /start: {}", row);
                db.updateRow(row);
                sendMessage(chatId, SEND_LOCATION_MESSAGE);
                sendPhoto(chatId, Paths.get("config").resolve("attachment_img.jpg"));
            } else {
                sendMessage(chatId, "You route is in progress. Please wait for it.");
            }
        }
    }

    private void processRepeatCommand(long chatId, long msgDate, Update update, Row oldRow) {
        if (update.getMessage().hasText() && "/repeat".equals(update.getMessage().getText())) {
            if (oldRow == null) {
                sendMessage(chatId, "You need to build a route before /repeat.");
            } else if (oldRow.state() == State.GOT_ALL_ROUTES) {
                sendMessage(chatId, "We started to generate routes for you.");
                Row newRow = oldRow
                    .withState(State.SENT_DISTANCE)
                    .withDate(msgDate);
                db.updateRow(newRow);
            } else if (oldRow.state() == State.FAILED_TO_PROCESS) {
                sendMessage(chatId, "We couldn't build a route earlier - so we don't try it again:( Try another location/distance please");
            } else {
                sendMessage(chatId, "You need to finish your previous route for using /repeat command");
            }
        }
    }

    private void processLocationUpdate(@NotNull Row dbRow, long msgDate, Update update) {
        assert dbRow != null;
        final Long chatId = update.getMessage().getChatId();
        if (!update.getMessage().hasLocation()) {
            sendMessage(chatId, SEND_LOCATION_MESSAGE);
            sendPhoto(chatId, Paths.get("config").resolve("attachment_img.jpg"));
            return;
        }
        final Location location = update.getMessage().getLocation();
        final LatLon latLon = new LatLon(location.getLatitude(), location.getLongitude());

        final Row row = new Row(chatId, update.getMessage().getChat().getUserName(), msgDate, State.SENT_LOCATION, latLon);
        assert dbRow.state() == State.CREATED;
        db.updateRow(row);
        sendMessage(chatId, SEND_DISTANCE_MESSAGE);
    }

    private void processDistanceUpdate(@NotNull Row dbRow, long date, Update update) {
        assert dbRow != null;
        final Long chatId = update.getMessage().getChatId();
        if (!update.getMessage().hasText()) {
            sendMessage(chatId, SEND_DISTANCE_MESSAGE);
            return;
        }
        String text = update.getMessage().getText();
        text = text.trim();
        if (text.startsWith("\"") || text.startsWith("“")) {
            text = text.substring(1);
        }
        if (text.endsWith("\"") || text.endsWith("”")) {
            text = text.substring(0, text.length() - 1);
        }
        final String[] splitText = text.split(" ");
        if (splitText.length == 2) {
            final Integer minDistance = Utils.parseInteger(splitText[0]);
            final Integer maxDistance = Utils.parseInteger(splitText[1]);
            if (minDistance == null ||
                maxDistance == null ||
                minDistance < 30 ||
                maxDistance < minDistance ||
                minDistance > 350 ||
                maxDistance - minDistance < 10) {
                sendMessage(chatId, SEND_DISTANCE_MESSAGE);
            } else {
                final String userName = update.getMessage().getChat().getUserName();
                Row row = new Row(chatId, userName, date, State.SENT_DISTANCE, dbRow.startPoint(), minDistance, maxDistance);
                db.updateRow(row);
                sendMessage(chatId, WAITING_FOR_RESULT);
            }
        } else {
            sendMessage(chatId, SEND_DISTANCE_MESSAGE);
        }
    }

    public void sendMessage(long chatId, String strMessage) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(strMessage);
        message.disableWebPagePreview();

        RuntimeException ex = null;
        for (int retry = 0; retry < SEND_RETRIES; retry++) {
            try {
                execute(message);
                telegramSentMessage.set(true);
                return;
            } catch (TelegramApiException e) {
                ex = new RuntimeException(e);
                Utils.sleep(5000);
                LOGGER.warn("couldn't send message to telegram, wait and try again, tries: {}", retry);
            }
        }
        assert ex != null;
        throw ex;
    }

    public void sendFile(long chatId, Path file) {
        SendDocument message = new SendDocument();
        message.setChatId(chatId);
        message.setDocument(new InputFile(file.toFile()));

        RuntimeException ex = null;
        for (int retry = 0; retry < SEND_RETRIES; retry++) {
            try {
                execute(message);
                telegramSentMessage.set(true);
                return;
            } catch (TelegramApiException e) {
                ex = new RuntimeException(e);
                Utils.sleep(5000);
                LOGGER.warn("couldn't send file to telegram, wait and try again, tries: {}", retry);
            }
        }
        assert ex != null;
        throw ex;
    }

    public void sendPhoto(long chatId, Path file) {
        SendPhoto message = new SendPhoto();
        message.setChatId(chatId);
        message.setPhoto(new InputFile(file.toFile()));

        RuntimeException ex = null;
        for (int retry = 0; retry < SEND_RETRIES; retry++) {
            try {
                execute(message);
                telegramSentMessage.set(true);
                return;
            } catch (TelegramApiException e) {
                ex = new RuntimeException(e);
                Utils.sleep(5000);
                LOGGER.warn("couldn't send photo to telegram, wait and try again, tries: {}", retry);
            }
        }
        assert ex != null;
        throw ex;
    }

    public static Bot startBot(Database db) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            var options = new DefaultBotOptions();
            options.setMaxThreads(1);
            final Bot bot = new Bot(options, db);
            botsApi.registerBot(bot);
            return bot;
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}