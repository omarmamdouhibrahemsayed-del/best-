import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Best Restaurant - pure Java (JDK 17+), no libraries needed.
 * Run:  javac BestServer.java && java BestServer
 * Open: http://localhost:8080      Orders: http://localhost:8080/admin?key=best123
 * Photos: put files named 1.jpg, 2.jpg ... (item id) inside a folder called "images" next to this file.
 */
public class BestServer {
    // ====== Settings ======
    static final int PORT = 8080;
    static final String ADMIN_KEY = "best123";          // change this!
    static final int FEE = 20;
    static final String VF = "01000000000";             // Vodafone Cash number
    static final String WHATS = "201000000000";         // WhatsApp number (with country code)
    static final Path ORDERS_FILE = Paths.get("orders.tsv");

    record Item(int id, String c, String e, int p, boolean hot, String nAr, String nEn, String dAr, String dEn) {}

    static final List<Item> MENU = List.of(
            new Item(1, "nag", "🍗", 95, false, "نجرسكو كلاسيك", "Classic Nagrisco", "قطع دجاج مقرمشة بتتبيلة البيت مع بطاطس وصوص", "Crispy chicken pieces, house seasoning, fries & sauce"),
            new Item(2, "nag", "🌶️", 105, true, "نجرسكو سبايسي", "Spicy Nagrisco", "مقرمش وحار بصوص الهالبينو الخاص", "Crunchy and hot with our jalapeño sauce"),
            new Item(3, "nag", "🧀", 115, false, "نجرسكو بالجبنة", "Cheesy Nagrisco", "محشي جبنة موتزاريلا سايحة", "Stuffed with melted mozzarella"),
            new Item(4, "piz", "🍕", 120, false, "بيتزا مارجريتا", "Margherita Pizza", "صوص طماطم، موتزاريلا وريحان", "Tomato sauce, mozzarella & basil"),
            new Item(5, "piz", "🍕", 145, false, "بيتزا بيبروني", "Pepperoni Pizza", "شرائح بيبروني وجبنة كتير", "Pepperoni slices & extra cheese"),
            new Item(6, "piz", "🍕", 160, false, "بيتزا ميكس جبن", "Four Cheese Pizza", "أربع أنواع جبنة مع عسل", "Four cheeses with a honey drizzle"),
            new Item(7, "fat", "🥟", 60, false, "فطيرة جبنة", "Cheese Fatayer", "عجينة هشة محشية جبنة", "Flaky pastry filled with cheese"),
            new Item(8, "fat", "🥟", 75, false, "فطيرة لحمة", "Meat Fatayer", "لحم مفروم متبل على أصوله", "Seasoned minced meat filling"),
            new Item(9, "fat", "🥟", 70, false, "فطيرة سجق", "Sausage Fatayer", "سجق بلدي وفلفل ألوان", "Sausage with colored peppers"),
            new Item(10, "cre", "🥞", 65, false, "كريب شوكولاتة", "Chocolate Crepe", "شوكولاتة بلجيكي وموز", "Belgian chocolate & banana"),
            new Item(11, "cre", "🌯", 85, false, "كريب فراخ", "Chicken Crepe", "فراخ مشوية، مشروم وصوص بيشاميل", "Grilled chicken, mushroom & béchamel"),
            new Item(12, "cre", "🥞", 90, false, "كريب ميكس", "Mixed Crepe", "مارشميلو، بسكوت وشوكولاتة", "Marshmallow, biscuit & chocolate")
    );

    static int seq = 10000;

    public static void main(String[] args) throws Exception {
        if (Files.exists(ORDERS_FILE)) seq += Files.readAllLines(ORDERS_FILE, StandardCharsets.UTF_8).size();
        HttpServer s = HttpServer.create(new InetSocketAddress(PORT), 0);
        s.createContext("/", ex -> send(ex, 200, "text/html; charset=utf-8", PAGE));
        s.createContext("/api/menu", BestServer::menu);
        s.createContext("/api/order", BestServer::order);
        s.createContext("/admin", BestServer::admin);
        s.createContext("/img/", BestServer::image);
        s.start();
        System.out.println("Best is running: http://localhost:" + PORT);
    }

    // ---------- helpers ----------
    static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        sendBytes(ex, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    static void sendBytes(HttpExchange ex, int code, String type, byte[] b) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, b.length);
        try (var o = ex.getResponseBody()) { o.write(b); }
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    static String h(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\t\\r\\n]", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    static Map<String, String> form(String body) {
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) {
            int i = kv.indexOf('=');
            if (i < 0) continue;
            m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    // ---------- /api/menu ----------
    static void menu(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("{\"fee\":" + FEE + ",\"vf\":\"" + VF + "\",\"whats\":\"" + WHATS + "\",\"items\":[");
        for (int i = 0; i < MENU.size(); i++) {
            Item m = MENU.get(i);
            String img = Files.exists(Paths.get("images", m.id() + ".jpg")) ? "/img/" + m.id() + ".jpg" : "";
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(m.id()).append(",\"c\":\"").append(m.c()).append("\",\"e\":\"").append(m.e())
                    .append("\",\"p\":").append(m.p()).append(",\"hot\":").append(m.hot() ? 1 : 0)
                    .append(",\"img\":\"").append(img).append("\",\"n\":{\"ar\":\"").append(esc(m.nAr())).append("\",\"en\":\"").append(esc(m.nEn()))
                    .append("\"},\"d\":{\"ar\":\"").append(esc(m.dAr())).append("\",\"en\":\"").append(esc(m.dEn())).append("\"}}");
        }
        send(ex, 200, "application/json; charset=utf-8", sb.append("]}").toString());
    }

    // ---------- /img/<id>.jpg ----------
    static void image(HttpExchange ex) throws IOException {
        String name = ex.getRequestURI().getPath().substring(5);
        Path p = Paths.get("images", name);
        if (!name.matches("[0-9]+\\.jpg") || !Files.exists(p)) { send(ex, 404, "text/plain", "Not found"); return; }
        sendBytes(ex, 200, "image/jpeg", Files.readAllBytes(p));
    }

    // ---------- /api/order (POST) ----------
    static synchronized void order(HttpExchange ex) throws IOException {
        String json = "application/json; charset=utf-8";
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, json, "{\"ok\":false}"); return; }
        Map<String, String> f = form(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String name = clean(f.get("name")), phone = clean(f.get("phone")).replace(" ", ""), addr = clean(f.get("address"));
        String pay = clean(f.get("pay")), ref = clean(f.get("ref"));
        boolean ok = name.length() > 1 && phone.matches("01[0-9]{9}") && addr.length() > 5
                && (pay.equals("cod") || pay.equals("visa") || pay.equals("vf"))
                && (!pay.equals("vf") || ref.length() > 3);
        int sub = 0;
        StringBuilder lines = new StringBuilder();
        if (ok) {
            try {
                for (String part : clean(f.get("items")).split(",")) {
                    String[] a = part.split(":");
                    int id = Integer.parseInt(a[0]), q = Integer.parseInt(a[1]);
                    Item it = MENU.stream().filter(m -> m.id() == id).findFirst().orElse(null);
                    if (it == null || q < 1 || q > 50) { ok = false; break; }
                    sub += it.p() * q;                      // price is always computed on the server
                    lines.append(q).append("x ").append(it.nEn()).append("; ");
                }
            } catch (Exception e) { ok = false; }
        }
        if (!ok || sub == 0) { send(ex, 400, json, "{\"ok\":false}"); return; }
        int total = sub + FEE;
        String no = "B" + (++seq);
        String row = String.join("\t", no, new Date().toString(), name, phone, addr, pay, ref, lines.toString(), String.valueOf(total));
        Files.writeString(ORDERS_FILE, row + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("New order " + no + " - " + total + " EGP - " + pay);
        send(ex, 200, json, "{\"ok\":true,\"no\":\"" + no + "\",\"total\":" + total + "}");
    }

    // ---------- /admin?key=... ----------
    static void admin(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getQuery();
        if (q == null || !form(q).getOrDefault("key", "").equals(ADMIN_KEY)) { send(ex, 403, "text/plain; charset=utf-8", "Forbidden"); return; }
        StringBuilder sb = new StringBuilder("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Orders</title>"
                + "<style>body{background:#0a0a0a;color:#f5f1e8;font-family:sans-serif;padding:20px}table{border-collapse:collapse;width:100%}"
                + "td,th{border:1px solid #2a2a2a;padding:8px;text-align:start;font-size:14px}th{color:#e8b04a}</style></head><body><h2>Best - Orders</h2>"
                + "<div style='overflow-x:auto'><table><tr><th>#</th><th>Time</th><th>Name</th><th>Phone</th><th>Address</th><th>Pay</th><th>Ref</th><th>Items</th><th>Total</th></tr>");
        List<String> rows = Files.exists(ORDERS_FILE) ? Files.readAllLines(ORDERS_FILE, StandardCharsets.UTF_8) : List.of();
        for (int i = rows.size() - 1; i >= 0; i--) {
            sb.append("<tr>");
            for (String c : rows.get(i).split("\t", -1)) sb.append("<td>").append(h(c)).append("</td>");
            sb.append("</tr>");
        }
        send(ex, 200, "text/html; charset=utf-8", sb.append("</table></div></body></html>").toString());
    }

    // ---------- the website (HTML/CSS/JS shown in the browser) ----------
    static final String PAGE = """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>Best — Restaurant</title>
<style>
:root{--bg:#0a0a0a;--card:#161616;--line:#2a2a2a;--tx:#f5f1e8;--mut:#9a958a;--gold:#e8b04a;--red:#e5383b}
*{box-sizing:border-box}
html{scroll-behavior:smooth;color-scheme:dark}
body{margin:0;background:var(--bg);color:var(--tx);font-family:Cairo,Tahoma,system-ui,sans-serif}
header{position:sticky;top:0;z-index:20;display:flex;align-items:center;justify-content:space-between;padding:12px 20px;background:rgba(10,10,10,.92);backdrop-filter:blur(8px);border-bottom:1px solid var(--line)}
.logo{font-family:Georgia,serif;font-size:30px;color:var(--gold);letter-spacing:2px;font-weight:700}
.logo b{color:var(--red)}
.hb{display:flex;gap:10px;align-items:center}
button{font-family:inherit;cursor:pointer}
.btn{background:var(--gold);color:#111;border:0;border-radius:999px;padding:10px 20px;font-weight:800;font-size:15px}
.btn:hover{filter:brightness(1.1)}
.ghost{background:none;color:var(--tx);border:1px solid var(--line);border-radius:999px;padding:8px 14px;font-weight:600}
.cartb{position:relative}
.cnt{position:absolute;top:-6px;inset-inline-end:-6px;background:var(--red);color:#fff;border-radius:50%;min-width:20px;height:20px;font-size:12px;display:grid;place-items:center}
.hero{text-align:center;padding:70px 20px 50px;background:radial-gradient(circle at 50% 0,#2a1d0a 0,transparent 60%)}
.hero h1{font-family:Georgia,serif;font-size:clamp(48px,12vw,110px);margin:0;color:var(--gold);line-height:1}
.hero p{color:var(--mut);font-size:20px;max-width:560px;margin:14px auto 26px}
.feat{display:flex;flex-wrap:wrap;gap:12px;justify-content:center;margin:0 20px 10px}
.feat span{border:1px solid var(--line);border-radius:999px;padding:8px 16px;color:var(--mut);font-size:14px}
nav{display:flex;gap:10px;overflow-x:auto;padding:18px 20px;position:sticky;top:62px;z-index:10;background:var(--bg)}
nav button{white-space:nowrap;background:var(--card);color:var(--tx);border:1px solid var(--line);border-radius:999px;padding:9px 18px;font-weight:600}
nav button.on{background:var(--gold);color:#111;border-color:var(--gold)}
main{max-width:1150px;margin:0 auto;padding:10px 20px 80px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:20px}
.card{background:var(--card);border:1px solid var(--line);border-radius:18px;overflow:hidden;transition:.25s}
.card:hover{transform:translateY(-4px);border-color:var(--gold)}
.ph{height:170px;display:grid;place-items:center;font-size:76px;background:linear-gradient(135deg,#241a0c,#111);background-size:cover;background-position:center}
.cb{padding:16px}
.cb h3{margin:0 0 4px;font-size:18px}
.cb p{margin:0 0 14px;color:var(--mut);font-size:14px;min-height:40px}
.row{display:flex;justify-content:space-between;align-items:center}
.pr{color:var(--gold);font-weight:800;font-size:18px}
.add{background:var(--red);color:#fff;border:0;border-radius:10px;padding:8px 16px;font-weight:700}
.tag{display:inline-block;background:#3a1517;color:#ff8a8d;font-size:12px;border-radius:6px;padding:2px 8px;margin-inline-start:6px}
.ov{position:fixed;inset:0;background:rgba(0,0,0,.7);z-index:50;display:none}
.ov.show{display:block}
.dr{position:absolute;top:0;bottom:0;inset-inline-end:0;width:min(420px,100%);background:#111;border-inline-start:1px solid var(--line);padding:20px;overflow:auto}
.md{position:absolute;inset:0;margin:auto;width:min(520px,94%);height:fit-content;max-height:92%;overflow:auto;background:#111;border:1px solid var(--line);border-radius:18px;padding:22px}
.it{display:flex;gap:10px;align-items:center;padding:12px 0;border-bottom:1px solid var(--line)}
.it .nm{flex:1}
.q{display:flex;gap:8px;align-items:center}
.q button{width:28px;height:28px;border-radius:50%;border:1px solid var(--line);background:var(--card);color:var(--tx)}
.tot{display:flex;justify-content:space-between;padding:6px 0;color:var(--mut)}
.tot.g{color:var(--tx);font-weight:800;font-size:19px}
input[type=text],input[type=tel],textarea{width:100%;background:var(--card);border:1px solid var(--line);border-radius:10px;padding:11px;color:var(--tx);font-family:inherit;font-size:15px;margin:5px 0 10px}
label.pay{display:flex;gap:10px;align-items:center;border:1px solid var(--line);border-radius:12px;padding:12px;margin-bottom:8px;cursor:pointer}
label.pay:has(input:checked){border-color:var(--gold);background:#1c1608}
.box{background:var(--card);border-radius:12px;padding:12px;margin-bottom:10px;font-size:14px;color:var(--mut)}
.err{color:#ff7b7e;font-size:14px;min-height:20px}
.ok{text-align:center;padding:20px 0}
.ok .big{font-size:70px}
footer{border-top:1px solid var(--line);text-align:center;padding:30px 20px;color:var(--mut)}
.empty{text-align:center;color:var(--mut);padding:40px 0}
</style>
</head>
<body>
<header>
  <div class="logo">BE<b>S</b>T</div>
  <div class="hb">
    <button class="ghost" id="lg"></button>
    <button class="btn cartb" id="cb"><span id="cbt"></span><span class="cnt" id="cc">0</span></button>
  </div>
</header>
<section class="hero">
  <h1>Best</h1>
  <p id="sub"></p>
  <button class="btn" id="cta"></button>
</section>
<div class="feat" id="feat"></div>
<nav id="nav"></nav>
<main><div class="grid" id="grid"></div></main>
<footer id="ft"></footer>

<div class="ov" id="ovc"><div class="dr">
  <div class="row"><h2 id="ct"></h2><button class="ghost" data-x="ovc">✕</button></div>
  <div id="ci"></div><div id="cs"></div>
  <button class="btn" style="width:100%;margin-top:14px" id="go"></button>
</div></div>

<div class="ov" id="ovk"><div class="md" id="mk"></div></div>

<script>
var CFG={vf:"",whats:"",fee:20,cur:{ar:"ج.م",en:"EGP"}};
var MENU=[];
var CATS=[["all",{ar:"الكل",en:"All"}],["nag",{ar:"نجرسكو",en:"Nagrisco"}],["piz",{ar:"بيتزا",en:"Pizza"}],["fat",{ar:"فطاير",en:"Fatayer"}],["cre",{ar:"كريب",en:"Crepes"}]];
var L={
ar:{lg:"English",sub:"أحلى أكل… بأسرع توصيل. نجرسكو، بيتزا، فطاير وكريب.",cta:"اطلب دلوقتي",cart:"السلة",add:"أضف",empty:"السلة فاضية",sub2:"المجموع",fee:"التوصيل",tot:"الإجمالي",go:"إتمام الطلب",hot:"حار",
name:"الاسم",ph:"رقم الموبايل",ad:"العنوان بالتفصيل",pay:"طريقة الدفع",visa:"فيزا / كارت بنكي",vf:"فودافون كاش",cod:"الدفع عند الاستلام",
cn:"رقم الكارت",ex:"MM/YY",cv:"CVV",vfn:"حوّل الإجمالي على رقم فودافون كاش:",vfr:"رقم عملية التحويل",place:"تأكيد الطلب",req:"من فضلك املأ كل البيانات بشكل صحيح",
done:"تم استلام طلبك! 🎉",on:"رقم الطلب",eta:"التوصيل خلال 30–45 دقيقة",wa:"تابع الطلب على واتساب",close:"إغلاق",
f:["🚚 توصيل سريع","🔒 دفع آمن","🌍 Arabic · English","⭐ مكونات طازجة"],ft:"© Best Restaurant — كل الحقوق محفوظة",demo:"نموذج تجريبي: لا يتم سحب أي مبلغ فعلي قبل ربط بوابة الدفع، ولا يتم حفظ بيانات الكارت."},
en:{lg:"عربي",sub:"Great food, fast delivery. Nagrisco, pizza, fatayer & crepes.",cta:"Order now",cart:"Cart",add:"Add",empty:"Your cart is empty",sub2:"Subtotal",fee:"Delivery",tot:"Total",go:"Checkout",hot:"Hot",
name:"Full name",ph:"Mobile number",ad:"Full address",pay:"Payment method",visa:"Visa / Debit card",vf:"Vodafone Cash",cod:"Cash on delivery",
cn:"Card number",ex:"MM/YY",cv:"CVV",vfn:"Send the total to this Vodafone Cash number:",vfr:"Transfer reference number",place:"Place order",req:"Please fill in all fields correctly",
done:"Order received! 🎉",on:"Order no.",eta:"Delivery in 30–45 minutes",wa:"Track on WhatsApp",close:"Close",
f:["🚚 Fast delivery","🔒 Secure payment","🌍 Arabic · English","⭐ Fresh ingredients"],ft:"© Best Restaurant — All rights reserved",demo:"Demo form: no real charge until a payment gateway is connected, and card data is not stored."}
};
var lang="ar",cat="all",cart={};
try{lang=localStorage.getItem("bl")||"ar";cart=JSON.parse(localStorage.getItem("bc")||"{}")}catch(e){}
var $=function(i){return document.getElementById(i)};
function t(k){return L[lang][k]}
function money(v){return v.toLocaleString(lang==="ar"?"ar-EG":"en-US")+" "+CFG.cur[lang]}
function save(){try{localStorage.setItem("bc",JSON.stringify(cart));localStorage.setItem("bl",lang)}catch(e){}}
function count(){var n=0;for(var k in cart)n+=cart[k];return n}
function sub(){var s=0;MENU.forEach(function(m){if(cart[m.id])s+=m.p*cart[m.id]});return s}
function render(){
  document.documentElement.lang=lang;document.documentElement.dir=lang==="ar"?"rtl":"ltr";
  $("lg").textContent=t("lg");$("sub").textContent=t("sub");$("cta").textContent=t("cta");$("cbt").textContent="🛒 "+t("cart");$("ct").textContent=t("cart");$("go").textContent=t("go");$("ft").textContent=t("ft");
  $("feat").innerHTML=t("f").map(function(x){return "<span>"+x+"</span>"}).join("");
  $("nav").innerHTML=CATS.map(function(c){return '<button class="'+(c[0]===cat?"on":"")+'" data-c="'+c[0]+'">'+c[1][lang]+"</button>"}).join("");
  $("grid").innerHTML=MENU.filter(function(m){return cat==="all"||m.c===cat}).map(function(m){
    var bg=m.img?' style="background-image:url('+m.img+')"':"";
    return '<div class="card"><div class="ph"'+bg+">"+(m.img?"":m.e)+'</div><div class="cb"><h3>'+m.n[lang]+(m.hot?'<span class="tag">'+t("hot")+"</span>":"")+"</h3><p>"+m.d[lang]+'</p><div class="row"><span class="pr">'+money(m.p)+'</span><button class="add" data-a="'+m.id+'">+ '+t("add")+"</button></div></div></div>"}).join("");
  cartUI();
}
function cartUI(){
  $("cc").textContent=count();
  var ids=Object.keys(cart).filter(function(k){return cart[k]>0&&MENU.some(function(x){return x.id==k})});
  if(!ids.length){$("ci").innerHTML='<div class="empty">'+t("empty")+"</div>";$("cs").innerHTML="";$("go").style.display="none";return}
  $("go").style.display="block";
  $("ci").innerHTML=ids.map(function(k){var m=MENU.filter(function(x){return x.id==k})[0];
    return '<div class="it"><span style="font-size:30px">'+m.e+'</span><div class="nm"><b>'+m.n[lang]+"</b><br><small>"+money(m.p)+'</small></div><div class="q"><button data-m="'+k+'">−</button><b>'+cart[k]+'</b><button data-p="'+k+'">+</button></div></div>'}).join("");
  var s=sub();
  $("cs").innerHTML='<div class="tot"><span>'+t("sub2")+"</span><span>"+money(s)+'</span></div><div class="tot"><span>'+t("fee")+"</span><span>"+money(CFG.fee)+'</span></div><div class="tot g"><span>'+t("tot")+"</span><span>"+money(s+CFG.fee)+"</span></div>";
}
function chg(id,d){cart[id]=Math.max(0,(cart[id]||0)+d);save();cartUI();$("cc").textContent=count()}
function checkout(){
  $("ovc").classList.remove("show");
  $("mk").innerHTML='<div class="row"><h2>'+t("go")+'</h2><button class="ghost" data-x="ovk">✕</button></div>'+
  '<input type="text" id="fn" placeholder="'+t("name")+'"><input type="tel" id="fp" placeholder="'+t("ph")+'"><textarea id="fa" rows="2" placeholder="'+t("ad")+'"></textarea>'+
  "<b>"+t("pay")+"</b><div style='margin-top:8px'>"+
  '<label class="pay"><input type="radio" name="pm" value="cod" checked> 💵 '+t("cod")+"</label>"+
  '<label class="pay"><input type="radio" name="pm" value="visa"> 💳 '+t("visa")+"</label>"+
  '<label class="pay"><input type="radio" name="pm" value="vf"> 📱 '+t("vf")+"</label></div>"+
  '<div id="pf"></div><div class="err" id="er"></div>'+
  '<div class="tot g"><span>'+t("tot")+"</span><span>"+money(sub()+CFG.fee)+'</span></div><button class="btn" style="width:100%;margin-top:10px" id="pl">'+t("place")+"</button>";
  $("ovk").classList.add("show");payUI();
}
function payUI(){
  var v=document.querySelector("input[name=pm]:checked").value,h="";
  if(v==="visa")h='<div class="box">'+t("demo")+'</div><input type="text" id="cn" inputmode="numeric" maxlength="19" placeholder="'+t("cn")+'" dir="ltr"><div class="row" style="gap:10px"><input type="text" id="ex" placeholder="'+t("ex")+'" maxlength="5" dir="ltr"><input type="text" id="cv" placeholder="'+t("cv")+'" maxlength="4" inputmode="numeric" dir="ltr"></div>';
  if(v==="vf")h='<div class="box">'+t("vfn")+'<br><b style="color:var(--gold);font-size:20px" dir="ltr">'+CFG.vf+'</b></div><input type="text" id="vr" placeholder="'+t("vfr")+'" dir="ltr">';
  $("pf").innerHTML=h;
}
function place(){
  var n=$("fn").value.trim(),p=$("fp").value.trim().split(" ").join(""),a=$("fa").value.trim(),v=document.querySelector("input[name=pm]:checked").value,ok=n.length>1&&/^01[0-9]{9}$/.test(p)&&a.length>5;
  if(ok&&v==="visa"){var c=$("cn").value.split(" ").join("");ok=/^[0-9]{13,19}$/.test(c)&&/^(0[1-9]|1[0-2])[/][0-9]{2}$/.test($("ex").value)&&/^[0-9]{3,4}$/.test($("cv").value)}
  var ref=v==="vf"?$("vr").value.trim():"";
  if(ok&&v==="vf")ok=ref.length>3;
  if(!ok){$("er").textContent=t("req");return}
  var ids=Object.keys(cart).filter(function(k){return cart[k]>0});
  var items=ids.map(function(k){return k+":"+cart[k]}).join(",");
  var names=ids.map(function(k){var m=MENU.filter(function(x){return x.id==k})[0];return encodeURIComponent(cart[k]+"x "+m.n[lang])}).join("%0A");
  var body="name="+encodeURIComponent(n)+"&phone="+encodeURIComponent(p)+"&address="+encodeURIComponent(a)+"&pay="+v+"&ref="+encodeURIComponent(ref)+"&items="+items;
  $("pl").disabled=true;
  fetch("/api/order",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:body})
  .then(function(r){return r.json()})
  .then(function(r){
    if(!r.ok){$("er").textContent=t("req");$("pl").disabled=false;return}
    var NL="%0A";
    var wa="https://wa.me/"+CFG.whats+"?text="+encodeURIComponent("Best #"+r.no)+NL+encodeURIComponent(n+" "+p)+NL+encodeURIComponent(a)+NL+names+NL+encodeURIComponent(money(r.total)+" ("+v+")");
    cart={};save();render();
    $("mk").innerHTML='<div class="ok"><div class="big"></div><h2>'+t("done")+"</h2><p>"+t("on")+": <b style='color:var(--gold)'>#"+r.no+"</b></p><p>"+t("eta")+'</p><a class="btn" style="display:inline-block;text-decoration:none;margin:6px" href="'+wa+'" target="_blank" rel="noopener">'+t("wa")+'</a><br><button class="ghost" data-x="ovk" style="margin-top:8px">'+t("close")+"</button></div>";
  })
  .catch(function(){$("er").textContent=t("req");$("pl").disabled=false});
}
document.addEventListener("click",function(e){
  var d=e.target.closest("[data-c],[data-a],[data-m],[data-p],[data-x]");
  if(d){var s=d.dataset;
    if(s.c){cat=s.c;render()}
    else if(s.a){chg(+s.a,1)}
    else if(s.m){chg(+s.m,-1)}
    else if(s.p){chg(+s.p,1)}
    else if(s.x){$(s.x).classList.remove("show")}
    return}
  if(e.target.id==="lg"){lang=lang==="ar"?"en":"ar";save();render()}
  else if(e.target.closest("#cb")){$("ovc").classList.add("show")}
  else if(e.target.id==="cta"){$("nav").scrollIntoView()}
  else if(e.target.id==="go"){checkout()}
  else if(e.target.id==="pl"){place()}
  else if(e.target.classList.contains("ov")){e.target.classList.remove("show")}
});
document.addEventListener("change",function(e){if(e.target.name==="pm")payUI()});
render();
fetch("/api/menu").then(function(r){return r.json()}).then(function(d){MENU=d.items;CFG.fee=d.fee;CFG.vf=d.vf;CFG.whats=d.whats;render()});
</script>
</body>
</html>
""";
}
