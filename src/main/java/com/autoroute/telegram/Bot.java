package com.autoroute.telegram;

import com.autoroute.Utils;
import com.autoroute.osm.LatLon;
import com.autoroute.telegram.db.Database;
import com.autoroute.telegram.db.Row;
import com.autoroute.telegram.db.State;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
    private static final String TELEGRAM_KEY;

    private static final String SEND_LOCATION_MESSAGE = """
        Please send your location in the attachment menu. You can choose any location where you want to start your ride.
        You can forward your old message with location if you want to reuse the old one.""";

    private static final String SEND_DISTANCE_MESSAGE = """
        We got your location. Now please provide minimum and maximum distance for your trip via space.
        Difference between them should be more than 10km. Minimum distance is 20km. 
        They can't be more than 150km.
        Example: \"50 100\"""";

    private static final String WAITING_FOR_RESULT = """
        You sent all information, your routes are in progress. 
        Please be patient for them, it can takes hours for now. 
        Only 1 route will be generated for now, will be fixed later.
        """;

    static {
        try {
            TELEGRAM_KEY = Files.readString(Paths.get("config").resolve("telegramKey.txt"));
        } catch (IOException e) {
            throw new IllegalStateException("couldn't read telegram key");
        }
    }

    private final Database db;

    public Bot(DefaultBotOptions options, Database db) {
        super(options);
        this.db = db;
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
        if (update.hasMessage()) {
            final long chatId = update.getMessage().getChatId();
            @Nullable final Row dbRow = db.getRow(chatId);

            LOGGER.info("got update with chat_id: {}, row: {}", chatId, dbRow);
            if (dbRow == null || dbRow.state() == State.GOT_ALL_ROUTES) {
                processStartCommand(chatId, update, dbRow);
            } else {
                switch (dbRow.state()) {
                    case CREATED -> processLocationUpdate(dbRow, update);
                    case SENT_LOCATION -> processDistanceUpdate(dbRow, update);
                    case SENT_DISTANCE -> sendMessage(chatId, WAITING_FOR_RESULT);
//                    case GOT_FIRST_PART -> {
//                        break;
//                    }
                    case GOT_ALL_ROUTES -> throw new RuntimeException("UNREACHABLE");
                    default -> throw new RuntimeException("UNREACHABLE");
                }
            }
        }
    }

    private void processStartCommand(long chatId, Update update, Row oldRow) {
        if (update.getMessage().hasText() && "/start".equals(update.getMessage().getText())) {
            Row row = new Row(chatId, State.CREATED);
            if (oldRow == null) {
                LOGGER.info("insert new row on /start: {}", row);
                db.insertRow(row);
            } else {
                LOGGER.info("update row on /start: {}", row);
                db.updateRow(row);
            }
            sendMessage(chatId, SEND_LOCATION_MESSAGE);
        } else {
            sendMessage(chatId, "Please start bot with /start command.");
        }
    }

    private void processLocationUpdate(@NotNull Row dbRow, Update update) {
        assert dbRow != null;
        final Long chatId = update.getMessage().getChatId();
        if (!update.getMessage().hasLocation()) {
            sendMessage(chatId, SEND_LOCATION_MESSAGE);
            return;
        }
        final Location location = update.getMessage().getLocation();
        final LatLon latLon = new LatLon(location.getLatitude(), location.getLongitude());

        final Row row = new Row(chatId, State.SENT_LOCATION, latLon);
        assert dbRow.state() == State.CREATED;
        db.updateRow(row);
        sendMessage(chatId, SEND_DISTANCE_MESSAGE);
    }

    private void processDistanceUpdate(@NotNull Row dbRow, Update update) {
        assert dbRow != null;
        final Long chatId = update.getMessage().getChatId();
        if (!update.getMessage().hasText()) {
            sendMessage(chatId, SEND_DISTANCE_MESSAGE);
            return;
        }
        String text = update.getMessage().getText();
        final String[] splitText = text.split(" ");
        if (splitText.length == 2) {
            final Integer minDistance = Utils.parseInteger(splitText[0]);
            final Integer maxDistance = Utils.parseInteger(splitText[1]);
            if (minDistance == null ||
                maxDistance == null ||
                minDistance < 20 ||
                maxDistance < minDistance ||
                minDistance > 150 ||
                maxDistance - minDistance < 20 ||
                maxDistance > 150) {
                sendMessage(chatId, SEND_DISTANCE_MESSAGE);
            } else {
                Row row = new Row(chatId, State.SENT_DISTANCE, dbRow.startPoint(), minDistance, maxDistance);
                db.updateRow(row);
                sendMessage(chatId, WAITING_FOR_RESULT);
            }
        } else {
            sendMessage(chatId, SEND_DISTANCE_MESSAGE);
        }
    }


    private void processTextUpdate(Update update) {
        final Long chatId = update.getMessage().getChatId();
        final String userText = update.getMessage().getText();

        final String text;
        if (userText.startsWith("/start")) {
            text = "Please send your location in the attachment menu. You can choose any location where you want to start your ride.";
        } else {
            text = "Unknown command, please use /start to run the bot.";
        }
        sendMessage(chatId, text);
    }

    public void sendMessage(long chatId, String strMessage) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(strMessage);
        message.disableWebPagePreview();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendFile(long chatId, Path file) {
        SendDocument message = new SendDocument();
        message.setChatId(chatId);
        message.setDocument(new InputFile(file.toFile()));
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
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