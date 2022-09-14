package com.autoroute.telegram;

import com.autoroute.osm.LatLon;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Bot extends TelegramLongPollingBot {

    private static final String TELEGRAM_KEY;

    static {
        try {
            TELEGRAM_KEY = Files.readString(Paths.get("config").resolve("telegramKey.txt"));
        } catch (IOException e) {
            throw new IllegalStateException("couldn't read telegram key");
        }
    }

    public Bot(DefaultBotOptions options) {
        super(options);
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
        if (update.hasMessage() && update.getMessage().hasText()) {
            processTextUpdate(update);
        } else if (update.hasMessage() && update.getMessage().hasLocation()) {
            processLocationUpdate(update);
        }
    }

    private void processTextUpdate(Update update) {
        final Long chatId = update.getMessage().getChatId();
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        final String userText = update.getMessage().getText();

        final String text;
        if (userText.startsWith("/start")) {
            text = "Please send your location in the attachment menu. " +
                "You can choose any location where you want to start your ride.";
        } else {
            text = "Unknown command, please use /start to run the bot.";
        }
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            // XXX
        }
    }

    private void processLocationUpdate(Update update) {
        final Long chatId = update.getMessage().getChatId();
        final String userName = update.getMessage().getChat().getUserName();
        final Location location = update.getMessage().getLocation();
        final LatLon latLon = new LatLon(location.getLatitude(), location.getLongitude());

        // save to db

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("We got your location and started to process your routes. " +
            "It can take a while... Please be patient");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            // XXX
        }
    }

    public static void startBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            var options = new DefaultBotOptions();
            options.setMaxThreads(1);
            botsApi.registerBot(new Bot(options));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws TelegramApiException {
        Bot.startBot();
    }
}