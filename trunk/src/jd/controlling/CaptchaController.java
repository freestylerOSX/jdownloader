package jd.controlling;

import java.awt.Image;
import java.io.File;

import javax.imageio.ImageIO;

import jd.captcha.JACMethod;
import jd.captcha.JAntiCaptcha;
import jd.captcha.LetterComperator;
import jd.captcha.pixelgrid.Captcha;
import jd.config.Configuration;
import jd.config.SubConfiguration;
import jd.gui.UserIO;
import jd.utils.JDUtilities;

public class CaptchaController {

    private String methodname;
    private File captchafile;
    private JAntiCaptcha jac;
    private String explain;
    private String suggest;

    public CaptchaController(String method, File file, String suggest, String explain) {
        this.methodname = method;
        this.captchafile = file;
        this.explain = explain;
        this.suggest = suggest;
        jac = new JAntiCaptcha(JDUtilities.getJACMethodsDirectory(), methodname);
    }

    /**
     * Returns of the method is enabled.
     * 
     * @param method
     * @return
     */
    public boolean isJACMethodEnabled(String method) {
        return (JDUtilities.getConfiguration() == null || !JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_CAPTCHA_JAC_DISABLE, false)) && JACMethod.hasMethod(method) && JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_JAC_METHODS + "_" + method, true);
    }

    public String getCode(int flag) {

        if ((flag & UserIO.NO_JAC) > 0) return UserIO.getInstance().requestCaptchaDialog(flag, methodname, captchafile, suggest, explain);
        if (!isJACMethodEnabled(methodname)) {
            if ((flag & UserIO.NO_USER_INTERACTION) > 0) return null;

            return UserIO.getInstance().requestCaptchaDialog(flag | UserIO.NO_JAC, methodname, captchafile, suggest, explain);

        }

        Image captchaImage;
        try {
            captchaImage = ImageIO.read(captchafile);

            // MediaTracker mediaTracker = new MediaTracker(jf);
            // mediaTracker.addImage(captchaImage, 0);
            // try {
            // mediaTracker.waitForID(0);
            // } catch (InterruptedException e) {
            // return null;
            // }
            // mediaTracker.removeImage(captchaImage);

            Captcha captcha = jac.createCaptcha(captchaImage);
            String captchaCode = jac.checkCaptcha(captcha);
            if (jac.isExtern()) {
                if ((flag & UserIO.NO_USER_INTERACTION) == 0 && captchaCode == null || captchaCode.trim().length() == 0) {

                    captchaCode = UserIO.getInstance().requestCaptchaDialog(flag | UserIO.NO_JAC, methodname, captchafile, suggest, explain);
                }
                return captchaCode;

            }

            LetterComperator[] lcs = captcha.getLetterComperators();

            double vp = 0.0;
            if (lcs == null) {
                vp = 100.0;
            } else {
                for (LetterComperator element : lcs) {

                    if (element == null) {
                        vp = 100.0;
                        break;
                    }
                    vp = Math.max(vp, element.getValityPercent());
                }
            }

            if (vp > (double) SubConfiguration.getConfig("JAC").getIntegerProperty(Configuration.AUTOTRAIN_ERROR_LEVEL, 95)) {
                if ((flag & UserIO.NO_USER_INTERACTION) > 0) return captchaCode;
                return UserIO.getInstance().requestCaptchaDialog(flag | UserIO.NO_JAC, methodname, captchafile, captchaCode, null);
            } else {
                return captchaCode;
            }

        } catch (Exception e) {
            return null;
        }
    }
}
