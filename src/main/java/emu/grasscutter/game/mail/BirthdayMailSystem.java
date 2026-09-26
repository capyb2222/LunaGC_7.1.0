package emu.grasscutter.game.mail;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.config.ConfigContainer.GameOptions.BirthdayMailOptions;
import emu.grasscutter.config.ConfigContainer.GameOptions.BirthdayMailOptions.GiftItem;
import emu.grasscutter.game.mail.Mail.MailContent;
import emu.grasscutter.game.mail.Mail.MailItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.lang.Language;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class BirthdayMailSystem {
    private BirthdayMailSystem() {}
	
	public static void checkAndSend(Player player) {
		checkAndSend(player, LocalDate.now(ZoneId.systemDefault()));
	}	

    public static void checkAndSend(Player player, LocalDate today) {
        var options = GAME_OPTIONS.birthdayMail;
        if (!options.enabled) return;

        var birthday = player.getBirthday();
        if (birthday == null || birthday.getDay() <= 0) return;
        if (birthday.getMonth() != today.getMonthValue() || birthday.getDay() != today.getDayOfMonth())
            return;

        if (player.getLastBirthdayMailYear() >= today.getYear()) return;

        List<MailItem> gifts = new ArrayList<>();
        for (GiftItem gift : options.gifts) {
            gifts.add(new MailItem(gift.itemId, gift.count));
        }

        var content =
                new MailContent(
                        Language.translate(player, "mail.birthday.title"),
                        Language.translate(player, "mail.birthday.content", player.getNickname()));

        long expireTime = Utils.getCurrentSeconds() + (options.expireDays * 86400L);
        var mail = new Mail(content, gifts, expireTime);

        player.getMailHandler().sendMail(mail);

        player.setLastBirthdayMailYear(today.getYear());
        player.save();
    }
}