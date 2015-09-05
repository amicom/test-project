//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jd.PluginWrapper;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 30050 $", interfaceVersion = 2, names = { "evilangel.com" }, urls = { "http://(www\\.)?members\\.evilangel.com/(en/)?[A-Za-z0-9\\-_]+/(download/\\d+/\\d+p|film/\\d+)" }, flags = { 2 })
public class EvilAngelCom extends PluginForHost {

    public EvilAngelCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.evilangel.com/en/join");
    }

    @Override
    public String getAGBLink() {
        return "http://www.evilangel.com/en/terms";
    }

    private static final String HTML_LOGOUT = "id=\"headerLinkLogout\"";
    private static final String FILMLINK    = "http://(www\\.)?members\\.evilangel.com/en/[A-Za-z0-9\\-_]+/film/\\d+";
    private String              DLLINK      = null;

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    /**
     * NOTE: While making the plugin, the testaccount was banned temporarily and we didn't get new password/username from the user->Plugin
     * isn't 100% done yet! http://svn.jdownloader.org/issues/6793
     */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa != null) {
            String filename = null;
            login(aa, false);
            if (link.getDownloadURL().matches(FILMLINK)) {
                br.getPage(link.getDownloadURL());
                filename = br.getRegex("<h1 class=\"title\">([^<>\"]*?)</h1>").getMatch(0);
                if (filename == null) {
                    filename = br.getRegex("<h1 class=\"h1_title\">([^<>\"]*?)</h1>").getMatch(0);
                    if (filename == null) {
                        filename = br.getRegex("<h2 class=\"h2_title\">([^<>\"]*?)</h2>").getMatch(0);
                    }
                }
                if (filename == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                filename = Encoding.htmlDecode(filename.trim());
                /** INFO: There are also .wmv versions available but we prefer .mp4 here as 1080p is only available as .mp4 */
                final String[] qualities = { "1080p", "720p", "540p", "480p", "240p" };
                for (final String quality : qualities) {
                    DLLINK = br.getRegex("\"(/en/[A-Za-z0-9\\-_]+/download/\\d+/" + quality + "/download/mp4)\"").getMatch(0);
                    if (DLLINK != null) {
                        filename = filename + "-" + quality + ".mp4";
                        break;
                    }
                    if (DLLINK == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    DLLINK = "http://members.evilangel.com" + DLLINK;
                }
            } else {
                DLLINK = link.getDownloadURL();
            }
            final Browser br2 = br.cloneBrowser();
            // In case the link redirects to the finallink
            br2.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                con = openConnection(br2, DLLINK);
                if (!con.getContentType().contains("html")) {
                    link.setDownloadSize(con.getLongContentLength());
                    if (filename == null) {
                        link.setFinalFileName(Encoding.htmlDecode(getFileNameFromHeader(con)));
                    } else {
                        link.setFinalFileName(filename);
                    }
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                return AvailableStatus.TRUE;
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }

        } else {
            link.getLinkStatus().setStatusText("Links can only be checked and downloaded via account!");
            return AvailableStatus.UNCHECKABLE;
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        try {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        } catch (final Throwable e) {
            if (e instanceof PluginException) {
                throw (PluginException) e;
            }
        }
        throw new PluginException(LinkStatus.ERROR_FATAL, "Links can only be checked and downloaded via account!");
    }

    private static final String MAINPAGE = "http://evilangel.com";
    private static Object       LOCK     = new Object();

    @SuppressWarnings("unchecked")
    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                br.setCookie(MAINPAGE, "enterSite", "en");
                br.getHeaders().put("Accept-Language", "en-US,en;q=0.5");
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            this.br.setCookie(MAINPAGE, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(true);
                /* We re over 18 */
                br.setCookie("www.evilangel.com", "enterSite", "en");
                br.getPage("http://members.evilangel.com/en");
                if (br.containsHTML(">We are experiencing some problems\\!<")) {
                    final AccountInfo ai = new AccountInfo();
                    ai.setStatus("Your IP is banned. Please re-connect to get a new IP to be able to log-in!");
                    logger.info("Your IP is banned. Please re-connect to get a new IP to be able to log-in!");
                    account.setAccountInfo(ai);
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }

                final String[] csrftokens = br.getRegex("name=\"csrfToken\" value=\"([^<>\"]*?)\"").getColumn(0);
                final String back = br.getRegex("name=\"back\" value=\"([^<>\"]*?)\"").getMatch(0);
                if (csrftokens == null || csrftokens.length == 0 || back == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłąd wtyczki, skontaktuj się z Supportem JDownloadera!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                final String csrftoken = csrftokens[csrftokens.length - 1];

                final Date d = new Date();
                SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd");
                final String date = sd.format(d);
                sd = new SimpleDateFormat("k:mm");
                final String time = sd.format(d);
                final String timedatestring = date + " " + time;
                br.setCookie(MAINPAGE, "mDateTime", Encoding.urlEncode(timedatestring));
                br.setCookie(MAINPAGE, "mOffset", "2");
                br.setCookie(MAINPAGE, "origin", "promo");
                br.setCookie(MAINPAGE, "timestamp", Long.toString(System.currentTimeMillis()));
                br.setCookie(MAINPAGE, "_gat_tracker1", "1");
                br.setCookie(MAINPAGE, "_gat_tracker2", "1");
                br.setCookie(MAINPAGE, "_gat_tracker3", "1");
                br.setCookie(MAINPAGE, "_gat_tracker4", "1");
                final String captcha = br.getRegex("name=\"captcha\\[id\\]\" value=\"([a-z0-9]{32})\"").getMatch(0);
                String postData = "csrfToken=" + csrftoken + "&username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()) + "&submit=Click+here+to+login&mDate=&mTime=&mOffset=&back=" + Encoding.urlEncode(back);
                // Handle stupid login captcha
                if (captcha != null) {
                    final DownloadLink dummyLink = new DownloadLink(this, "Account", "evilangel.com", "http://evilangel.com", true);
                    final String code = getCaptchaCode("http://www.evilangel.com/en/captcha/" + captcha, dummyLink);
                    postData += "&captcha%5Bid%5D=" + captcha + "&captcha%5Binput%5D=" + Encoding.urlEncode(code);
                }
                br.postPage("http://www.evilangel.com/en/login", postData);
                if (br.containsHTML(">Your account is deactivated for abuse")) {
                    final AccountInfo ai = new AccountInfo();
                    ai.setStatus("Your account is deactivated for abuse. Please re-activate it to use it in JDownloader.");
                    logger.info("Your account is deactivated for abuse. Please re-activate it to use it in JDownloader.");
                    account.setAccountInfo(ai);
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "Your account is deactivated for abuse. Please re-activate it to use it in JDownloader.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                if (!br.containsHTML(HTML_LOGOUT)) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername, Passwort oder login Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłędny użytkownik/hasło lub kod Captcha wymagany do zalogowania!\r\nUpewnij się, że prawidłowo wprowadziłes hasło i nazwę użytkownika. Dodatkowo:\r\n1. Jeśli twoje hasło zawiera znaki specjalne, zmień je (usuń) i spróbuj ponownie!\r\n2. Wprowadź hasło i nazwę użytkownika ręcznie bez użycia opcji Kopiuj i Wklej.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or login captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = this.br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            // Prevent direct login to prevent login captcha
            login(account, false);
            br.getPage("http://members.evilangel.com/en");
            if (!br.containsHTML(HTML_LOGOUT)) {
                login(account, true);
            }
        } catch (final PluginException e) {
            account.setValid(false);
            throw e;
        }
        ai.setUnlimitedTraffic();
        account.setValid(true);
        ai.setStatus("Premium account");
        return ai;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, link.getDownloadURL(), true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private URLConnectionAdapter openConnection(final Browser br, final String directlink) throws IOException {
        URLConnectionAdapter con;
        if (isJDStable()) {
            con = br.openGetConnection(directlink);
        } else {
            con = br.openHeadConnection(directlink);
        }
        return con;
    }

    private boolean isJDStable() {
        return System.getProperty("jd.revision.jdownloaderrevision") == null;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("free"))) {
            /* free accounts also have captchas */
            return true;
        }
        return false;
    }
}