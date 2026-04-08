package com.performetriks.performator.scripts;

import com.performetriks.performator.http.PFRHttp;
import com.performetriks.performator.http.PFRHttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DebugRedirect {
    public static void main(String[] args) throws Throwable {
        PFRHttp.defaultTrustAllCertificates(true);
        PFRHttp.defaultUseVirtualThreads(false);

        System.out.println("--- STEP 1: Catalog ---");
        PFRHttp.create("Catalog", "https://jpetstore.perfluencer.pl/jpetstore/actions/Catalog.action").send();

        System.out.println("--- STEP 2: SignonForm ---");
        PFRHttpResponse formResponse = PFRHttp.create("SignonForm", "https://jpetstore.perfluencer.pl/jpetstore/actions/Account.action?signonForm=").send();
        
        String body = formResponse.getBody();
        String csrf = extract(body, "name=\"_csrf\" value=\"([^\"]+)\"");
        String sp = extract(body, "name=\"_sourcePage\" value=\"([^\"]+)\"");
        String fp = extract(body, "name=\"__fp\" value=\"([^\"]+)\"");
        
        System.out.println("CSRF: " + csrf);
        System.out.println("SourcePage: " + sp);
        System.out.println("FP: " + fp);

        System.out.println("--- STEP 3: Login (No Follow Redirects) ---");
        PFRHttpResponse loginResponse = PFRHttp.create("Login", "https://jpetstore.perfluencer.pl/jpetstore/actions/Account.action")
                .METHOD("POST")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .disableFollowRedirects()
                .param("username", "user3")
                .param("password", "password3")
                .param("signon", "Login")
                .param("_csrf", csrf)
                .param("_sourcePage", sp)
                .param("__fp", fp)
                .send();

        System.out.println("Status: " + loginResponse.getStatus());
        System.out.println("Headers: " + loginResponse.getHeadersAsJson());

        System.out.println("--- COOKIES ---");
        for(org.apache.hc.client5.http.cookie.Cookie c : com.performetriks.performator.http.PFRHttp.getContext().getCookieStore().getCookies()) {
            System.out.println("Cookie: " + c.getName() + "=" + c.getValue() + " | Path=" + c.getPath() + " | Domain=" + c.getDomain());
        }

        String location = loginResponse.getHeadersAsMap().get("Location");
        System.out.println("Location: " + location);

        System.out.println("--- FILTERING COOKIES (DISABLED) ---");
        /*
        org.apache.hc.client5.http.cookie.CookieStore store = com.performetriks.performator.http.PFRHttp.getContext().getCookieStore();
        java.util.List<org.apache.hc.client5.http.cookie.Cookie> cookies = new java.util.ArrayList<>(store.getCookies());
        store.clear();
        for(org.apache.hc.client5.http.cookie.Cookie c : cookies) {
            if("/".equals(c.getPath())) {
                System.out.println("Removing cookie: " + c.getName());
                continue;
            }
            store.addCookie(c);
        }
        */

        if (location != null) {
            // Make location absolute if it's relative
            if (!location.startsWith("http")) {
                location = "https://jpetstore.perfluencer.pl" + location;
            }
            System.out.println("--- STEP 4: Follow Redirect (Manual) ---");
            PFRHttpResponse redResponse = PFRHttp.create("Redirect1", location).disableFollowRedirects().send();
            System.out.println("Status: " + redResponse.getStatus());
            System.out.println("Location: " + redResponse.getHeadersAsMap().get("Location"));
            System.out.println("Body Contains Sign Out: " + redResponse.getBody().contains("Sign Out"));
        }
    }

    private static String extract(String body, String regex) {
        Matcher m = Pattern.compile(regex).matcher(body);
        return m.find() ? m.group(1) : "NOT_FOUND";
    }
}
