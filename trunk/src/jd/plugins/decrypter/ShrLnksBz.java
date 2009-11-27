//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.gui.UserIO;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "share-links.biz" }, urls = { "http://[\\w\\.]*?share-links\\.biz/_[0-9a-z]+" }, flags = { 0 })
public class ShrLnksBz extends PluginForDecrypt {

    public ShrLnksBz(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(false);
        br.getPage(parameter);

        /* Error handling */
        if (br.containsHTML("Der Inhalt konnte nicht gefunden werden")) throw new DecrypterException(JDL.L("plugins.decrypt.errormsg.unavailable", "Perhaps wrong URL or the download is not available anymore."));
        // Folderpassword+Captcha handling
        if (br.containsHTML("id=\"folderpass\"")) {
            boolean wrongpw = false;
            String latestPassword = null;
            for (int i = 0; i <= 3; i++) {
                Form pwform = br.getForm(0);
                if (pwform == null) return null;
                // First try the stored password, if that doesn't work, ask the
                // user to enter it
                latestPassword = this.getPluginConfig().getStringProperty("PASSWORD");
                if (latestPassword == null || wrongpw == true) latestPassword = Plugin.getUserInput("Password?", param);
                pwform.put("pass", latestPassword);
                br.submitForm(pwform);
                if (br.containsHTML("Das eingegebene Passwort ist falsch")) {
                    wrongpw = true;
                    continue;
                }
                break;
            }
            if (br.containsHTML("Das eingegebene Passwort ist falsch")) {
                logger.warning("Wrong password!");
                throw new DecrypterException(DecrypterException.PASSWORD);
            }
            // Save actual password if it is valid
            getPluginConfig().setProperty("PASSWORD", latestPassword);
            getPluginConfig().save();
        }

        if (br.containsHTML("(/captcha/|captcha_container|\"Captcha\"|id=\"captcha\")")) {
            for (int i = 0; i <= 5; i++) {
                String Captchamap = br.getRegex("/><img src=\"(.*?)\" alt=\"Captcha\" id=\"captcha\" usemap=\"#captchamap\" />").getMatch(0);
                File file = this.getLocalCaptchaFile();
                Browser temp = br.cloneBrowser();
                temp.getDownload(file, "http://share-links.biz" + Captchamap);
                Point p = UserIO.getInstance().requestClickPositionDialog(file, "Share-links.biz", JDL.L("plugins.decrypt.shrlnksbz.desc", "Read the combination in the background and click the corresponding combination in the overview!"));
                int y = getnearstvalue(br.getRegex("coords=\"\\d+,\\d+,\\d+,(\\d+?)\"").getColumn(0), p.y);
                int x = getnearstvalue(br.getRegex("coords=\"\\d+,\\d+,(\\d+?)," + y + "\"").getColumn(0), p.x);
                String nexturl = br.getRegex("<area shape=\"rect\" coords=\"\\d+,\\d+," + x + "," + y + "\" href=\"/(.*?)\" alt=\"\" title=\"\" />").getMatch(0);
                if (nexturl == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                br.setFollowRedirects(true);
                br.getPage("http://share-links.biz/" + nexturl);
                if (br.containsHTML("Die getroffene Auswahl war falsch")) {
                    br.getPage(parameter);
                    // Usually a cookie is being set and you don't have to enter
                    // the password again but if you would, the password of the
                    // plugin configuration would always be the right one
                    if (br.containsHTML("id=\"folderpass\"")) {
                        Form pwform = br.getForm(0);
                        pwform.put("pass", this.getPluginConfig().getStringProperty("PASSWORD"));
                        br.submitForm(pwform);
                    }
                    continue;
                }
                break;
            }
            if (br.containsHTML("Die getroffene Auswahl war falsch")) throw new DecrypterException(DecrypterException.CAPTCHA);
        }

        // container handling
        if (br.containsHTML("'dlc'")) {
            decryptedLinks = loadcontainer(br, "dlc");
            if (decryptedLinks != null && decryptedLinks.size() > 0) return decryptedLinks;
        }

        if (br.containsHTML("'rsdf'")) {
            decryptedLinks = loadcontainer(br, "rsdf");
            if (decryptedLinks != null && decryptedLinks.size() > 0) return decryptedLinks;
        }

        if (br.containsHTML("'ccf'")) {
            decryptedLinks = loadcontainer(br, "ccf");
            if (decryptedLinks != null && decryptedLinks.size() > 0) return decryptedLinks;
        }
        if (br.containsHTML("'cnl'")) {
            decryptedLinks = loadcontainer(br, "cnl");
            if (decryptedLinks != null && decryptedLinks.size() > 0) return decryptedLinks;

        } else {
            logger.warning("The user tried to add a link without containers but the plugin can't handle such links!");
            throw new DecrypterException(JDL.L("plugins.decrypt.errormsg.unavailable", "Perhaps wrong URL or the download is not available anymore."));
        }
        /* File package handling */
        String[] links = br.getRegex("decrypt\\.gif\".*?_get\\('(.*?)'").getColumn(0);
        if (links == null || links.length == 0) return null;
        progress.setRange(links.length);
        for (String link : links) {
            link = "http://share-links.biz/get/lnk/" + link;
            br.getPage(link);
            System.out.print(br.toString());
            String clink0 = br.getRegex("unescape\\(\"(.*?)\"").getMatch(0);
            if (clink0 == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            clink0 = Encoding.htmlDecode(clink0);
            String frm = br.getRegex("\"(http://share-links\\.biz/get/frm/.*?)\"").getMatch(0);
            String cmm = br.getRegex("\"(http://share-links\\.biz/get/cmm/.*?)\"").getMatch(0);
            br.getPage(cmm);
            System.out.print(br.toString());
            br.getPage(frm);
            System.out.print(br.toString());
            DownloadLink dl = createDownloadlink(cmm);
            decryptedLinks.add(dl);
            progress.increase(1);
        }
        return decryptedLinks;
    }

    private int getnearstvalue(String[] data, int cord) {
        int min = Integer.parseInt(data[0]);
        for (String element : data) {
            if (min > Integer.parseInt(element)) min = Integer.parseInt(element);
        }
        int search = 0;
        if (cord < min / 2)
            search = min;
        else
            search = Math.round((float) cord / min);
        return min * search;
    }

    // by jiaz
    private ArrayList<DownloadLink> loadcontainer(Browser br, String format) throws IOException, PluginException {
        Browser brc = br.cloneBrowser();
        String dlclinks = null;
        if (format.matches("dlc")) {
            dlclinks = br.getRegex("dlc container.*?onclick=\"javascript:_get\\('(.*?)'.*?'dlc'\\)").getMatch(0);
        }
        if (format.matches("ccf")) {
            dlclinks = br.getRegex("ccf container.*?onclick=\"javascript:_get\\('(.*?)'.*?'ccf'\\)").getMatch(0);
        }
        if (format.matches("rsdf")) {
            dlclinks = br.getRegex("rsdf container.*?onclick=\"javascript:_get\\('(.*?)'.*?'rsdf'\\)").getMatch(0);
        }
        if (dlclinks == null) return null;
        dlclinks = "http://share-links.biz/get/" + format + "/" + dlclinks;
        String test = Encoding.htmlDecode(dlclinks);
        File file = null;
        URLConnectionAdapter con = brc.openGetConnection(dlclinks);
        if (con.getResponseCode() == 200) {
            file = JDUtilities.getResourceFile("tmp/sharelinks/" + test.replaceAll("(http://share-links.biz/|/|\\?)", "") + "." + format);
            if (file == null) return null;
            file.deleteOnExit();
            brc.downloadConnection(file, con);
        } else {
            con.disconnect();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }

        if (file != null && file.exists() && file.length() > 100) {
            ArrayList<DownloadLink> decryptedLinks = JDUtilities.getController().getContainerLinks(file);
            if (decryptedLinks.size() > 0) return decryptedLinks;
        } else {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        return null;
    }

}
