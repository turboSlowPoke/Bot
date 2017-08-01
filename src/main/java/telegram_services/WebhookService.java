package telegram_services;

import configs.GlobalConfigs;
import database_service.DbService;
import database_service.NoUserInDb;
import entitys.TaskStatus;
import entitys.TaskType;
import entitys.Tasks;
import entitys.User;
import org.apache.log4j.Logger;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.CallbackQuery;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by kuteynikov on 14.07.2017.
 */
public class WebhookService extends TelegramWebhookBot  {
    private static final Logger log = Logger.getLogger(WebhookService.class);
    private DbService dbService;
    private ReplyKeyboardMarkup mainMenuMarkup;
    private ReplyKeyboardMarkup subscripMenuMarkup;
    private ReplyKeyboardMarkup infoMenuMarkup;
    private ReplyKeyboardMarkup settingsMenuMarkup;
    private ReplyKeyboardMarkup partnerMenuMarkup;
    private InlineKeyboardMarkup trialInlineButton;
    private InlineKeyboardMarkup paymentsBonusButton;

    public WebhookService() {
        this.dbService = DbService.getInstance();
        this.mainMenuMarkup = MenuCreator.createMainMenuMarkup();
        this.subscripMenuMarkup = MenuCreator.createSubscripMenuMarkup();
        this.infoMenuMarkup = MenuCreator.createInfoMenuMarkup();
        this.settingsMenuMarkup = MenuCreator.createSettingsMenuMarkup();
        this.partnerMenuMarkup = MenuCreator.createPartnersMenu();
        this.trialInlineButton = MenuCreator.createTrialInlineButton();
        this.paymentsBonusButton = MenuCreator.createPaymentsBonusButton();
    }



    @Override
    public BotApiMethod onWebhookUpdateReceived(Update update) {
        System.out.println(update.getMessage().getText());
        if (update.hasCallbackQuery()) {
            //System.out.println("пришел CallbackQuery: " + update.getCallbackQuery());
            EditMessageText editMessageText = callBackContext(update.getCallbackQuery());
            return editMessageText;
        } else if(update.getMessage().getChat().isSuperGroupChat()){
            System.out.println("сообщение из группового чата id="+update.getMessage().getChatId());
            if (update.getMessage().getText().equals("test"))
             return new SendMessage(update.getMessage().getChatId(),"трололо");
            else
                return null;
        } else if (update.hasMessage()&update.getMessage().hasText()){
            long userId = update.getMessage().getChat().getId();
            SendMessage sendMessage;
            if (!dbService.dbHasUser(userId)){
                sendMessage = startContext(update.getMessage());
            }else if (update.getMessage().getText().startsWith("/")&&!update.getMessage().getText().equals("/start")){
                sendMessage = adminCommandContext(update.getMessage());
            } else {
                sendMessage = mainContext(update.getMessage());
            }
            return sendMessage;
        } else {
            return null;
        }
    }

    public SendMessage startContext(Message message) {
        //вытаскиваем данные из сообщения и создаем пользователя
        long userID = message.getChat().getId();
        String userName = "@"+message.getChat().getUserName();
        String firstName = message.getChat().getFirstName();
        String lastName = message.getChat().getLastName();
        long chatID = message.getChatId();
        User newUser = new User(userID, userName, firstName, lastName, chatID);
        newUser.setEndDateOfSubscription(LocalDateTime.now().plusDays(1)); //включаем тестовый период
        log.info("New User created: "+newUser);
        //готовим сообщение для ответа
        SendMessage replyMessage = new SendMessage().setChatId(chatID).enableMarkdown(true);
        String welcomeText="*"+firstName + "*, рады приветствовать Вас в проекте New Wave, мы готовы предоставить, лучшие сигналы для торговли на криптовалютном рынке\"\n" +
                "                    + \"\\nУ вас бесплатный тестовый период 1 день, если вам всё понравилось, то купите пописку";
        welcomeText=userName.equals("@null")?welcomeText+"\n*Внимание!* У Вас не заполнен *Username* в настройках *Telegram*, он необходим для взаимодействия с Вами," +
                "пожалуйста задайте его , затем(чтобы обновить в нашей базе), в меню бота нажмите *Параметры* -> *Мои данные*":welcomeText;
        //проверяем start без параметров=без приглашения||с параметрами=приглашенный
        String start= message.getText();
        if (start.equals("/start")) {
            dbService.addRootUser(newUser);
            log.info("В базу добавлен не приглашенный пользователь "+newUser);
            replyMessage.setText(welcomeText);
            replyMessage.setReplyMarkup(mainMenuMarkup);
        } else if (start.startsWith("/start ")) {
            String stringID = message.getText().substring(7);
            Long parentuserID = Long.parseLong(stringID);
            try {
                dbService.addChildrenUser(parentuserID, newUser);
                replyMessage.setText(welcomeText);
                replyMessage.setReplyMarkup(mainMenuMarkup);
                log.info("В базу добавлен приглашённый пользователь: " + newUser);
            } catch (NoUserInDb noUserInDb) {
                dbService.addRootUser(newUser);
                log.info("Ошибка в id пригласителя");
                replyMessage.setText( welcomeText+"\nОшибка в id пригласителя, ссылка по котрой вы перешли не корректна");
                replyMessage.setReplyMarkup(mainMenuMarkup);
            }
        } else {
            log.info("пользователь"+userID+userName+" не в базе шлёт сообщение :" + message.getText());
            replyMessage.setText("Ошибка! Тебя еще нет в базе, отправь  /start").enableMarkdown(false);
        }
        return replyMessage;
    }

    public SendMessage mainContext(Message incomingMessage) {
        String texOfMessage = incomingMessage.getText();
        SendMessage message = new SendMessage()
                .setChatId(incomingMessage.getChatId())
                .enableMarkdown(true);
        CommandButtons button = CommandButtons.getTYPE(texOfMessage);
        User user = null;
        switch (button) {
            case START:
                message.setText(BotMessages.MAIN_MENU.getText());
                message.setReplyMarkup(mainMenuMarkup);
                break;
            case OFORMIT_PODPISCU:
                message.setText(BotMessages.SELECT_SUBSCRIPTION.getText());
                message.setReplyMarkup(subscripMenuMarkup);
                break;
            case INFO_BOT:
                message.setText(BotMessages.SHORT_DESCRIPTION.getText());
                message.setReplyMarkup(infoMenuMarkup);
                break;
            case GENERAL_DESCRIPTION:
                message.setText(BotMessages.GENERAL_DESCRIPTION.getText());
                break;
            case FAQ:
                message.setText(BotMessages.FAQ.getText());
                break;
            case HOW_TO_CHANGE_CURRENCY:
                message.setText(BotMessages.HOW_TO_CHANGE_CURRENCY.getText());
                break;
            case SUPPORT:
                message.setText(BotMessages.SUPPORT.getText());
                break;
            case BACK_IN_MAIN_MENU:
                message.setText(BotMessages.MAIN_MENU.getText());
                message.setReplyMarkup(mainMenuMarkup);
                break;
            case ONE_MONTH:
                message.setText(BotMessages.ONE_MONTH.getText());
                message.setReplyMarkup(
                        MenuCreator.createPayButton("userId="+incomingMessage.getChat().getId()+"&typeOfParchase=oneMonth"));
                break;
            case TWO_MONTH:
                message.setText(BotMessages.TWO_MONTH.getText());
                message.setReplyMarkup(
                        MenuCreator.createPayButton("userId="+incomingMessage.getChat().getId()+"&typeOfParchase=twoMonth"));
                break;
            case THREE_MONTH:
                message.setText(BotMessages.THREE_MONTH.getText());
                message.setReplyMarkup(
                        MenuCreator.createPayButton("userId="+incomingMessage.getChat().getId()+"&typeOfParchase=threeMonth"));
                break;
            case CHECK_SUBSCRIPTION:
                user = dbService.getUserFromDb(incomingMessage.getChat().getId());
                LocalDate endDate = user.getServices().getEndDateOfSubscription().toLocalDate();
                if (user.getServices().getUnlimit()) {
                    message.setText("У вас безлимитная подписка!");
                } else if (endDate != null) {
                    if (endDate.isAfter(LocalDate.now())) {
                        message.setText(BotMessages.CHECK_SUBSCRIPTION.getText() + endDate);
                    } else {
                        message.setText("Ваша подписка истекла: " + endDate);
                    }
                } else {
                    message.setText("У вас еще не было подписки"
                            + "\n ");
                    message.setReplyMarkup(trialInlineButton);
                }
                break;
            case PRIVATE_CHAT:
                User userPC = dbService.getUserFromDb(incomingMessage.getChatId());
                if (userPC.getServices().getOnetimeConsultation()
                        ||userPC.getServices().getUnlimit()){
                    message.setText("Оставьте заявку и вас пригласят в чат");
                    message.setReplyMarkup(MenuCreator.createInlineButton(CommandButtons.TASK_PRIVATE_CHAT));
                } else {
                    message.setText("Кнопка запроса будет доступна после оплаты." +
                            "\n Стоимость персональной консультации 6р,");
                    message.setReplyMarkup(MenuCreator.createPayButton("userId="+incomingMessage.getChat().getId()+"&typeOfParchase=oneTimeConsultation"));
                }
                break;
            case UNLIMIT:
                message.setText(BotMessages.UNLIMIT_SUBSCRIPTION.getText());
                message.setReplyMarkup(
                        MenuCreator.createPayButton("userId="+incomingMessage.getChat().getId()+"&typeOfParchase=unlimit"));
                break;
            case INVITE_TO_CHAT:
                message.setText("тут будет ссылочка на чат");
                break;
            case SETTINGS:
                message.setText(BotMessages.SETTINGS_MENU.getText());
                message.setReplyMarkup(settingsMenuMarkup);
                break;
            case SITE_ACCOUNT:
                message.setText("сайт в разработке, скоро здесь можно будет получить данные для входа");
                break;
            case REQUISITES:
                String wallet = dbService.getUserFromDb(incomingMessage.getChat().getId()).getAdvcashWallet();
                message.setText("id вашего кошелька Advcash="+wallet
                            +"\nЧтобы сменить, отправьте:"
                            +"\n/acwallet id_кошелька").enableMarkdown(false);
                break;
            case MY_DATA:
                String firstName = incomingMessage.getChat().getFirstName();
                String lastName =incomingMessage.getChat().getLastName();
                String userName = "@"+incomingMessage.getChat().getUserName();
                dbService.updatePersonalData(firstName,lastName,userName,incomingMessage.getChatId());
                user = dbService.getUserFromDb(incomingMessage.getChatId());
                String s = "Ваш username: " + user.getPersonalData().getUserNameTelegram()
                        +"\nВаше имя: " + user.getPersonalData().getFirstName()
                        +"\nВаша фамилия: "+user.getPersonalData().getLastName()
                        +"\nВаш Id: "+user.getUserID();
                message.setText(s).enableMarkdown(false);
                break;
            case PARTNER_PROGRAM:
                message.setText(CommandButtons.PARTNER_PROGRAM.getText());
                message.setReplyMarkup(partnerMenuMarkup);
                break;
            case INVITE_PARTNER:
                message.setText("Чтобы пригласить участника, скопируйте и отправьте ему эту ссылку "
                        + "\n https://t.me/TheNewWaveBot?start=" + incomingMessage.getChat().getId()
                        + "\n");
                break;
            case CHECK_REFERALS:
                user = dbService.getUserFromDb(incomingMessage.getChat().getId());
                int parentLevel = user.getLevel();
                int parentLeftKey = user.getLeftKey();
                int parentRightKey = user.getRightKey();
                String text;
                if (parentLeftKey + 1 == parentRightKey) {
                    text = "У вас нет рефералов";
                } else {
                    List<User> userList = dbService.getChildrenUsers(parentLevel, parentLeftKey, parentRightKey);
                    String level1 = "";
                    String level2 = "";
                    String level3 = "";
                    for (User u : userList) {
                        if (parentLevel + 1 == u.getLevel()) {
                            level1 = level1 + " " + u.getUserName() + "-" + u.getFirstName()+ "-";
                            level1=u.getAdvcashTransactions()!=null&&u.getAdvcashTransactions().size()>0?level1+"+"+"\n":level1+"\n";
                        } else if (parentLevel + 2 == u.getLevel()) {
                            level2 = level2 + " " + u.getUserName() + "-" + u.getFirstName()+";\n";
                            level2=u.getAdvcashTransactions()!=null&&u.getAdvcashTransactions().size()>0?level2+"+"+"\n":level2+"\n";
                        } else {
                            level3 = level3 + " " + u.getUserName() + "-" + u.getFirstName()+"\n";
                            level3=u.getAdvcashTransactions()!=null&&u.getAdvcashTransactions().size()>0?level3+"+"+"\n":level3+"\n";
                        }
                    }
                    text = "*Рефералы 1го уровня:* "
                            + "\n_Количество оплативших подписку=_"+user.getPersonalData().getReferalsForPrize().size()
                            + "\n" + level1
                            + "\n*Рефералы 2го уровня:* "
                            + "\n" + level2
                            + "\n*Рефералы 3го уровня:* "
                            + "\n" + level3;
                }
                message.setText(text);
                break;
            case LOCAL_WALLET:
                 user = dbService.getUserFromDb(incomingMessage.getChat().getId());
                if (user.getPersonalData().getReferalsForPrize().size()>0&&user.getPersonalData().getReferalsForPrize().size()%10>user.getPersonalData().getCountPrize()){
                    BigDecimal cash = user.getLocalWallet();
                    String string = "На вашем счету: *"+cash +"*"
                            +"\n"+user.getPersonalData().getReferalsForPrize().size()+" приглашенных вами пользователей оплатили подписку и вам полагается премия 1000$"
                            +"\n\nОставьте заявку, чтобы вывести бонусы  и получить премию на ваш счет."
                            + "\n Проверьте, правильно ли указан ваш кошелек(если нет, смените его в настройках)."
                            + "\n Ваш Advcash:"+user.getAdvcashWallet()
                            + "\n Зявки обрабатываются в конце недели.\n";
                    message.setText(string);
                    message.setReplyMarkup(MenuCreator.createInlineButton(CommandButtons.REQUEST_PRIZE_BUTTON));
                } else {
                    BigDecimal cash = user.getLocalWallet();
                    String string = "На вашем счету: *" + cash + "*"
                            + "\n\nОставьте заявку, чтобы вывести бонусы на ваш счет."
                            + "\n Проверьте, правильно ли указан ваш кошелек(если нет, смените его в настройках)."
                            + "\n Ваш Advcash:" + user.getAdvcashWallet()
                            + "\n Баланс должен быть положительным."
                            + "\n Зявки обрабатываются в конце недели.\n";
                    message.setText(string);
                    message.setReplyMarkup(paymentsBonusButton);
                }
                break;
            default:
                message.setText(BotMessages.DEFAULT.getText());
        }
        return message;
    }

    public EditMessageText callBackContext(CallbackQuery callbackQuery) {
        String dataFromQuery = callbackQuery.getData();
        CommandButtons button = CommandButtons.getTYPE(dataFromQuery);
        EditMessageText new_message = new EditMessageText()
                .setChatId(callbackQuery.getMessage().getChatId())
                .setMessageId(callbackQuery.getMessage().getMessageId());
        switch (button) {
            case REQUEST_PAYMENT_BUTTON:
                User user = dbService.getUserFromDb(callbackQuery.getMessage().getChat().getId());
                System.out.println("достали пользователя " + user);
                BigDecimal cash = user.getPersonalData().getLocalWallet();
                System.out.println("cash=" + cash);
                List<Tasks> tasks = user.getTasks();
                Tasks task = null;
                System.out.println("проверяем наличие заявки");
                if (tasks != null && tasks.size() > 0) {
                    for (Tasks t : tasks) {
                        if (t.getStatus().equals(TaskStatus.OPEN) && t.getType().equals(TaskType.PAY_BONUSES))
                            task = t;
                    }
                }
                int checkCash = -1;
                if (cash != null)
                    checkCash = cash.compareTo(new BigDecimal("0.01"));
                System.out.println("checkCash=" + checkCash);
                String string = "";
                if (user.getPersonalData().getAdvcashWallet() == null) {
                    System.out.println("Заявка не принята, не установлен кошелёк Advcash.");
                    string = "Заявка не принята, не установлен кошелёк Advcash.";
                } else if (checkCash == -1) {
                    string = "Заявка не принята, не достаточно средств.";
                    System.out.println(string);
                } else if (task != null) {
                    string = " Вы уже подали заявку " + task.getDateTimeOpening();
                    System.out.println(string);
                } else {
                    task = new Tasks(TaskType.PAY_BONUSES, user);
                    dbService.addTask(user.getUserID(), task);
                    string = "Заявка принята!";
                    System.out.println(string);
                }
                System.out.println(string);
                new_message.setText(string);
                break;
            case REQUEST_PRIZE_BUTTON:
                user = dbService.getUserFromDb(callbackQuery.getMessage().getChat().getId());
                System.out.println("достали пользователя " + user);
                cash = user.getPersonalData().getLocalWallet();
                System.out.println("cash=" + cash);
                tasks = user.getTasks();
                task = null;
                System.out.println("проверяем наличие заявки");
                if (tasks != null && tasks.size() > 0) {
                    for (Tasks t : tasks) {
                        if (t.getStatus().equals(TaskStatus.OPEN) && t.getType().equals(TaskType.PAY_PRIZE))
                            task = t;
                    }
                }
                checkCash = -1;
                if (cash != null)
                    checkCash = cash.compareTo(new BigDecimal("0.01"));
                System.out.println("checkCash=" + checkCash);
                string = "";
                if (user.getPersonalData().getAdvcashWallet() == null) {
                    System.out.println("Заявка не принята, не установлен кошелёк Advcash.");
                    string = "Заявка не принята, не установлен кошелёк Advcash.";
                } else if (checkCash == -1) {
                    string = "Заявка не принята, не достаточно средств.";
                    System.out.println(string);
                } else if (task != null) {
                    string = " Вы уже подали заявку " + task.getDateTimeOpening();
                    System.out.println(string);
                } else {
                    task = new Tasks(TaskType.PAY_BONUSES, user);
                    dbService.addTask(user.getUserID(), task);
                    string = "Заявка принята!";
                    System.out.println(string);
                }
                System.out.println(string);
                new_message.setText(string);
                break;
            case TASK_PRIVATE_CHAT:
                user = dbService.getUserFromDb(callbackQuery.getMessage().getChat().getId());
                task = user.getCurrentTasks(TaskType.PRIVATE_CHAT);
                if (task != null) {
                    new_message.setText("У вас уже открыта заявка на персональный чат.\n" + task);
                } else {
                    task = new Tasks(TaskType.PRIVATE_CHAT, user);
                    dbService.addTask(user.getUserID(), task);
                    List<User> managers = dbService.getManagers();
                    for (User manager : managers) {
                        SendMessage sendMessage = new SendMessage()
                                .setChatId(manager.getChatID())
                                .setText("Новая заявка на аудит портфеля:"
                                        + "\nuserName: " + user.getUserName()
                                        + "\nuserId: " + user.getUserID()
                                        + "\nВремя создания: " + task.getDateTimeOpening());
                        try {
                            sendApiMethod(sendMessage);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }
                    new_message.setText("Запрос принят, ждите приглашения в чат");
                }
                break;
            default:
                new_message.setText("Что то пошло не так обратитесь в тех. поддрежку");
                break;
        }
        return new_message;

    }


    public SendMessage adminCommandContext(Message incomingMessage) {
        SendMessage replyMessage = new SendMessage()
                .setChatId(incomingMessage.getChatId())
                .setText("Что-то пошло не так");
        String textIncomingMessage = incomingMessage.getText();
        if (textIncomingMessage.startsWith(CommandButtons.SEND_SIGNAL.getText())){
            if(dbService.getUserFromDb(incomingMessage.getChat().getId()).getTypeUser().equals("manager")) {
                List<Long> usersId = dbService.getSubscribers();
                if (usersId != null && usersId.size() > 0) {
                    for (Long uId : usersId) {
                        SendMessage sendMessage = new SendMessage()
                                .setChatId(uId)
                                .setText(textIncomingMessage.substring(8));
                        try {
                            sendApiMethod(sendMessage);
                        } catch (TelegramApiException e) {
                            System.out.println("Не смог отправить сигнал "+uId);
                            e.printStackTrace();
                        }
                    }
                    replyMessage.setText("Сигнал отправлен " + usersId.size() + " пользователям");
                }
            } else{
                replyMessage.setText(" у вас недостаточно прав для этой команды");
            }
        } else if(textIncomingMessage.startsWith(CommandButtons.CHANGE_AC_WALLET.getText())) {
            String wallet = textIncomingMessage.substring(10);
            dbService.getUserFromDb(incomingMessage.getChat().getId())
                    .getPersonalData().setAdvcashWallet(wallet);
            replyMessage.setText("Кошелек id="+wallet+" установлен");
        } else if(textIncomingMessage.equals(CommandButtons.CHECK_PRIVATE_CHAT.getText())){
            List<Tasks> tasks = dbService.getTasks(TaskStatus.OPEN,TaskType.PRIVATE_CHAT);
            if (tasks!=null&&tasks.size()>0){
                StringBuilder stringBuilder = new StringBuilder();
                for (Tasks t : tasks){
                    stringBuilder.append(tasks).append("\n******\n");
                }
                replyMessage.setText(stringBuilder.toString());
            } else {
                replyMessage.setText("Заявок нет");
            }
        } else if(textIncomingMessage.equals(CommandButtons.CHECK_TASKS_PAYMENT.getText())){
            List<Tasks> tasks = dbService.getTasks(TaskStatus.OPEN,TaskType.PAY_BONUSES);
            if (tasks!=null&&tasks.size()>0){
                StringBuilder stringBuilder = new StringBuilder();
                for (Tasks t : tasks){
                    stringBuilder.append(tasks).append("\n******\n");
                }
                replyMessage.setText(stringBuilder.toString());
            } else {
                replyMessage.setText("заявок нет");
            }
        } else if (textIncomingMessage.equals(CommandButtons.SET_MENEGERS_MENU.getText())){
            replyMessage.setText("меню");
            replyMessage.setReplyMarkup(MenuCreator.createAdminMenuMarkup());
        }
        return replyMessage;
    }

    public void sendTimer(SendMessage sendMessage){
        try {
            sendApiMethod(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }



    @Override
    public String getBotUsername() {
        return GlobalConfigs.botname;
    }

    @Override
    public String getBotToken() {
        return GlobalConfigs.token;
    }

    @Override
    public String getBotPath() {
        return GlobalConfigs.botPath;
    }

}
