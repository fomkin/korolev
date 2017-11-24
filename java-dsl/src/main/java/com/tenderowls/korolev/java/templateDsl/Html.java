package com.tenderowls.korolev.java.templateDsl;

import com.tenderowls.korolev.java.effects.Effect;
import levsha.Document;
import levsha.Document.Node;
import levsha.Document.Attr;
import levsha.XmlNs;

public class Html<State, Message> {

  public final Node<Effect<State, Message>> htmlTag(String name, Document<Effect<State, Message>>[] content) {
    return renderContext -> {
      renderContext.openNode(XmlNs.html(), name);
      for (Document<Effect<State, Message>> child : content) {
        child.apply(renderContext);
      }
      renderContext.closeNode(name);
    };
  }

  // HTML tags

  public final Attr<Effect<State, Message>> htmlAttr(String name, String value) {
    return renderContext -> renderContext.setAttr(XmlNs.html(), name, value);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> a(Document<Effect<State, Message>>... content) {
    return htmlTag("a", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> abbr(Document<Effect<State, Message>>... content) {
    return htmlTag("abbr", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> acronym(Document<Effect<State, Message>>... content) {
    return htmlTag("acronym", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> address(Document<Effect<State, Message>>... content) {
    return htmlTag("address", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> applet(Document<Effect<State, Message>>... content) {
    return htmlTag("applet", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> area(Document<Effect<State, Message>>... content) {
    return htmlTag("area", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> article(Document<Effect<State, Message>>... content) {
    return htmlTag("article", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> aside(Document<Effect<State, Message>>... content) {
    return htmlTag("aside", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> audio(Document<Effect<State, Message>>... content) {
    return htmlTag("audio", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> b(Document<Effect<State, Message>>... content) {
    return htmlTag("b", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> base(Document<Effect<State, Message>>... content) {
    return htmlTag("base", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> basefont(Document<Effect<State, Message>>... content) {
    return htmlTag("basefont", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> bdi(Document<Effect<State, Message>>... content) {
    return htmlTag("bdi", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> bdo(Document<Effect<State, Message>>... content) {
    return htmlTag("bdo", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> bgsound(Document<Effect<State, Message>>... content) {
    return htmlTag("bgsound", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> big(Document<Effect<State, Message>>... content) {
    return htmlTag("big", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> blink(Document<Effect<State, Message>>... content) {
    return htmlTag("blink", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> blockquote(Document<Effect<State, Message>>... content) {
    return htmlTag("blockquote", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> body(Document<Effect<State, Message>>... content) {
    return htmlTag("body", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> br(Document<Effect<State, Message>>... content) {
    return htmlTag("br", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> button(Document<Effect<State, Message>>... content) {
    return htmlTag("button", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> canvas(Document<Effect<State, Message>>... content) {
    return htmlTag("canvas", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> caption(Document<Effect<State, Message>>... content) {
    return htmlTag("caption", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> center(Document<Effect<State, Message>>... content) {
    return htmlTag("center", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> cite(Document<Effect<State, Message>>... content) {
    return htmlTag("cite", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> code(Document<Effect<State, Message>>... content) {
    return htmlTag("code", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> col(Document<Effect<State, Message>>... content) {
    return htmlTag("col", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> colgroup(Document<Effect<State, Message>>... content) {
    return htmlTag("colgroup", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> command(Document<Effect<State, Message>>... content) {
    return htmlTag("command", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> content(Document<Effect<State, Message>>... content) {
    return htmlTag("content", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> data(Document<Effect<State, Message>>... content) {
    return htmlTag("data", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> datalist(Document<Effect<State, Message>>... content) {
    return htmlTag("datalist", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> dd(Document<Effect<State, Message>>... content) {
    return htmlTag("dd", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> del(Document<Effect<State, Message>>... content) {
    return htmlTag("del", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> details(Document<Effect<State, Message>>... content) {
    return htmlTag("details", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> dfn(Document<Effect<State, Message>>... content) {
    return htmlTag("dfn", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> dialog(Document<Effect<State, Message>>... content) {
    return htmlTag("dialog", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> dir(Document<Effect<State, Message>>... content) {
    return htmlTag("dir", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> div(Document<Effect<State, Message>>... content) {
    return htmlTag("div", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> dl(Document<Effect<State, Message>>... content) {
    return htmlTag("dl", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> dt(Document<Effect<State, Message>>... content) {
    return htmlTag("dt", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> element(Document<Effect<State, Message>>... content) {
    return htmlTag("element", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> em(Document<Effect<State, Message>>... content) {
    return htmlTag("em", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> embed(Document<Effect<State, Message>>... content) {
    return htmlTag("embed", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> fieldset(Document<Effect<State, Message>>... content) {
    return htmlTag("fieldset", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> figcaption(Document<Effect<State, Message>>... content) {
    return htmlTag("figcaption", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> figure(Document<Effect<State, Message>>... content) {
    return htmlTag("figure", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> font(Document<Effect<State, Message>>... content) {
    return htmlTag("font", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> footer(Document<Effect<State, Message>>... content) {
    return htmlTag("footer", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> form(Document<Effect<State, Message>>... content) {
    return htmlTag("form", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> frame(Document<Effect<State, Message>>... content) {
    return htmlTag("frame", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> frameset(Document<Effect<State, Message>>... content) {
    return htmlTag("frameset", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> h1(Document<Effect<State, Message>>... content) {
    return htmlTag("h1", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> h2(Document<Effect<State, Message>>... content) {
    return htmlTag("h2", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> h3(Document<Effect<State, Message>>... content) {
    return htmlTag("h3", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> h4(Document<Effect<State, Message>>... content) {
    return htmlTag("h4", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> h5(Document<Effect<State, Message>>... content) {
    return htmlTag("h5", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> h6(Document<Effect<State, Message>>... content) {
    return htmlTag("h6", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> head(Document<Effect<State, Message>>... content) {
    return htmlTag("head", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> header(Document<Effect<State, Message>>... content) {
    return htmlTag("header", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> hgroup(Document<Effect<State, Message>>... content) {
    return htmlTag("hgroup", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> hr(Document<Effect<State, Message>>... content) {
    return htmlTag("hr", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> html(Document<Effect<State, Message>>... content) {
    return htmlTag("html", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> i(Document<Effect<State, Message>>... content) {
    return htmlTag("i", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> iframe(Document<Effect<State, Message>>... content) {
    return htmlTag("iframe", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> image(Document<Effect<State, Message>>... content) {
    return htmlTag("image", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> img(Document<Effect<State, Message>>... content) {
    return htmlTag("img", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> input(Document<Effect<State, Message>>... content) {
    return htmlTag("input", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> ins(Document<Effect<State, Message>>... content) {
    return htmlTag("ins", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> isindex(Document<Effect<State, Message>>... content) {
    return htmlTag("isindex", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> kbd(Document<Effect<State, Message>>... content) {
    return htmlTag("kbd", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> keygen(Document<Effect<State, Message>>... content) {
    return htmlTag("keygen", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> label(Document<Effect<State, Message>>... content) {
    return htmlTag("label", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> legend(Document<Effect<State, Message>>... content) {
    return htmlTag("legend", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> li(Document<Effect<State, Message>>... content) {
    return htmlTag("li", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> link(Document<Effect<State, Message>>... content) {
    return htmlTag("link", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> listing(Document<Effect<State, Message>>... content) {
    return htmlTag("listing", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> main(Document<Effect<State, Message>>... content) {
    return htmlTag("main", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> map(Document<Effect<State, Message>>... content) {
    return htmlTag("map", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> mark(Document<Effect<State, Message>>... content) {
    return htmlTag("mark", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> marquee(Document<Effect<State, Message>>... content) {
    return htmlTag("marquee", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> menu(Document<Effect<State, Message>>... content) {
    return htmlTag("menu", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> menuitem(Document<Effect<State, Message>>... content) {
    return htmlTag("menuitem", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> meta(Document<Effect<State, Message>>... content) {
    return htmlTag("meta", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> meter(Document<Effect<State, Message>>... content) {
    return htmlTag("meter", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> multicol(Document<Effect<State, Message>>... content) {
    return htmlTag("multicol", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> nav(Document<Effect<State, Message>>... content) {
    return htmlTag("nav", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> nextid(Document<Effect<State, Message>>... content) {
    return htmlTag("nextid", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> nobr(Document<Effect<State, Message>>... content) {
    return htmlTag("nobr", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> noembed(Document<Effect<State, Message>>... content) {
    return htmlTag("noembed", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> noframes(Document<Effect<State, Message>>... content) {
    return htmlTag("noframes", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> noscript(Document<Effect<State, Message>>... content) {
    return htmlTag("noscript", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> object(Document<Effect<State, Message>>... content) {
    return htmlTag("object", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> ol(Document<Effect<State, Message>>... content) {
    return htmlTag("ol", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> optgroup(Document<Effect<State, Message>>... content) {
    return htmlTag("optgroup", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> option(Document<Effect<State, Message>>... content) {
    return htmlTag("option", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> output(Document<Effect<State, Message>>... content) {
    return htmlTag("output", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> p(Document<Effect<State, Message>>... content) {
    return htmlTag("p", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> param(Document<Effect<State, Message>>... content) {
    return htmlTag("param", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> picture(Document<Effect<State, Message>>... content) {
    return htmlTag("picture", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> plaintext(Document<Effect<State, Message>>... content) {
    return htmlTag("plaintext", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> pre(Document<Effect<State, Message>>... content) {
    return htmlTag("pre", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> progress(Document<Effect<State, Message>>... content) {
    return htmlTag("progress", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> q(Document<Effect<State, Message>>... content) {
    return htmlTag("q", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> rp(Document<Effect<State, Message>>... content) {
    return htmlTag("rp", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> rt(Document<Effect<State, Message>>... content) {
    return htmlTag("rt", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> rtc(Document<Effect<State, Message>>... content) {
    return htmlTag("rtc", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> ruby(Document<Effect<State, Message>>... content) {
    return htmlTag("ruby", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> s(Document<Effect<State, Message>>... content) {
    return htmlTag("s", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> samp(Document<Effect<State, Message>>... content) {
    return htmlTag("samp", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> script(Document<Effect<State, Message>>... content) {
    return htmlTag("script", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> section(Document<Effect<State, Message>>... content) {
    return htmlTag("section", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> select(Document<Effect<State, Message>>... content) {
    return htmlTag("select", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> shadow(Document<Effect<State, Message>>... content) {
    return htmlTag("shadow", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> slot(Document<Effect<State, Message>>... content) {
    return htmlTag("slot", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> small(Document<Effect<State, Message>>... content) {
    return htmlTag("small", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> source(Document<Effect<State, Message>>... content) {
    return htmlTag("source", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> spacer(Document<Effect<State, Message>>... content) {
    return htmlTag("spacer", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> span(Document<Effect<State, Message>>... content) {
    return htmlTag("span", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> strike(Document<Effect<State, Message>>... content) {
    return htmlTag("strike", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> strong(Document<Effect<State, Message>>... content) {
    return htmlTag("strong", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> style(Document<Effect<State, Message>>... content) {
    return htmlTag("style", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> sub(Document<Effect<State, Message>>... content) {
    return htmlTag("sub", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> summary(Document<Effect<State, Message>>... content) {
    return htmlTag("summary", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> sup(Document<Effect<State, Message>>... content) {
    return htmlTag("sup", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> table(Document<Effect<State, Message>>... content) {
    return htmlTag("table", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> tbody(Document<Effect<State, Message>>... content) {
    return htmlTag("tbody", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> td(Document<Effect<State, Message>>... content) {
    return htmlTag("td", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> template(Document<Effect<State, Message>>... content) {
    return htmlTag("template", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> textarea(Document<Effect<State, Message>>... content) {
    return htmlTag("textarea", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> tfoot(Document<Effect<State, Message>>... content) {
    return htmlTag("tfoot", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> th(Document<Effect<State, Message>>... content) {
    return htmlTag("th", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> thead(Document<Effect<State, Message>>... content) {
    return htmlTag("thead", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> time(Document<Effect<State, Message>>... content) {
    return htmlTag("time", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> title(Document<Effect<State, Message>>... content) {
    return htmlTag("title", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> tr(Document<Effect<State, Message>>... content) {
    return htmlTag("tr", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> track(Document<Effect<State, Message>>... content) {
    return htmlTag("track", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> tt(Document<Effect<State, Message>>... content) {
    return htmlTag("tt", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> u(Document<Effect<State, Message>>... content) {
    return htmlTag("u", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> ul(Document<Effect<State, Message>>... content) {
    return htmlTag("ul", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> var(Document<Effect<State, Message>>... content) {
    return htmlTag("var", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> video(Document<Effect<State, Message>>... content) {
    return htmlTag("video", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> wbr(Document<Effect<State, Message>>... content) {
    return htmlTag("wbr", content);
  }

  @SafeVarargs
  final public Node<Effect<State, Message>> xmp(Document<Effect<State, Message>>... content) {
    return htmlTag("xmp", content);
  }

  // Attributes

  /**
   * List of types the server accepts, typically a file type.
   * {{{<form>, <input>}}}
   */
  final public Attr<Effect<State, Message>> accept(String value) {
    return htmlAttr("accept", value);
  }

  /**
   * List of supported charsets.
   * {{{<form>}}}
   */
  final public Attr<Effect<State, Message>> acceptCharset(String value) {
    return htmlAttr("accept-charset", value);
  }

  /**
   * Defines a keyboard shortcut to activate or add focus to the element.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> accesskey(String value) {
    return htmlAttr("accesskey", value);
  }

  /**
   * The URI of a program that processes the information submitted via the form.
   * {{{<form>}}}
   */
  final public Attr<Effect<State, Message>> action(String value) {
    return htmlAttr("action", value);
  }

  /**
   * Specifies the horizontal alignment of the element.
   * {{{<applet>, <caption>, <col>, <colgroup>,  <hr>, <iframe>, <img>, <table>, <tbody>,  <td>,  <tfoot> , <th>, <thead>, <tr>}}}
   */
  final public Attr<Effect<State, Message>> align(String value) {
    return htmlAttr("align", value);
  }

  /**
   * Alternative text in case an image can't be displayed.
   * {{{<applet>, <area>, <img>, <input>}}}
   */
  final public Attr<Effect<State, Message>> alt(String value) {
    return htmlAttr("alt", value);
  }

  /**
   * Indicates that the script should be executed asynchronously.
   * {{{<script>}}}
   */
  final public Attr<Effect<State, Message>> async(String value) {
    return htmlAttr("async", value);
  }

  /**
   * Indicates whether controls in this form can by default have their values automatically completed by the browser.
   * {{{<form>, <input>}}}
   */
  final public Attr<Effect<State, Message>> autocomplete(String value) {
    return htmlAttr("autocomplete", value);
  }

  /**
   * The element should be automatically focused after the page loaded.
   * {{{<button>, <input>, <keygen>, <select>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> autofocus(String value) {
    return htmlAttr("autofocus", value);
  }

  /**
   * The audio or video should play as soon as possible.
   * {{{<audio>, <video>}}}
   */
  final public Attr<Effect<State, Message>> autoplay(String value) {
    return htmlAttr("autoplay", value);
  }

  /**
   * Contains the time range of already buffered media.
   * {{{<audio>, <video>}}}
   */
  final public Attr<Effect<State, Message>> buffered(String value) {
    return htmlAttr("buffered", value);
  }

  /**
   * A challenge string that is submitted along with the public key.
   * {{{<keygen>}}}
   */
  final public Attr<Effect<State, Message>> challenge(String value) {
    return htmlAttr("challenge", value);
  }

  /**
   * Declares the character encoding of the page or script.
   * {{{<meta>, <script>}}}
   */
  final public Attr<Effect<State, Message>> charset(String value) {
    return htmlAttr("charset", value);
  }

  /**
   * Indicates whether the element should be checked on page load.
   * {{{<command>, <input>}}}
   */
  final public Attr<Effect<State, Message>> checked(String value) {
    return htmlAttr("checked", value);
  }

  /**
   * Contains a URI which points to the source of the quote or change.
   * {{{<blockquote>, <del>, <ins>, <q>}}}
   */
  final public Attr<Effect<State, Message>> cite(String value) {
    return htmlAttr("cite", value);
  }

  /**
   * Often used with CSS to style elements with common properties.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> $class(String value) {
    return htmlAttr("class", value);
  }

  /**
   * Specifies the URL of the applet's class file to be loaded and executed.
   * {{{<applet>}}}
   */
  final public Attr<Effect<State, Message>> code(String value) {
    return htmlAttr("code", value);
  }

  /**
   * This attribute gives the absolute or relative URL of the directory where applets' .class files referenced by the code attribute are stored.
   * {{{<applet>}}}
   */
  final public Attr<Effect<State, Message>> codebase(String value) {
    return htmlAttr("codebase", value);
  }

  /**
   * Defines the number of columns in a textarea.
   * {{{<textarea>}}}
   */
  final public Attr<Effect<State, Message>> cols(String value) {
    return htmlAttr("cols", value);
  }

  /**
   * The colspan attribute defines the number of columns a cell should span.
   * {{{<td>, <th>}}}
   */
  final public Attr<Effect<State, Message>> colspan(String value) {
    return htmlAttr("colspan", value);
  }

  /**
   * A value associated with http-equiv or name depending on the context.
   * {{{<meta>}}}
   */
  final public Attr<Effect<State, Message>> content(String value) {
    return htmlAttr("content", value);
  }

  /**
   * Indicates whether the element's content is editable.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> contenteditable(String value) {
    return htmlAttr("contenteditable", value);
  }

  /**
   * Defines the ID of a <menu> element which will serve as the element's context menu.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> contextmenu(String value) {
    return htmlAttr("contextmenu", value);
  }

  /**
   * Indicates whether the browser should show playback controls to the user.
   * {{{<audio>, <video>}}}
   */
  final public Attr<Effect<State, Message>> controls(String value) {
    return htmlAttr("controls", value);
  }

  /**
   * A set of values specifying the coordinates of the hot-spot region.
   * {{{<area>}}}
   */
  final public Attr<Effect<State, Message>> coords(String value) {
    return htmlAttr("coords", value);
  }

  /**
   * How the element handles cross-origin requests
   * {{{<audio>, <img>, <link>, <script>, <video>}}}
   */
  final public Attr<Effect<State, Message>> crossorigin(String value) {
    return htmlAttr("crossorigin", value);
  }

  /**
   * Specifies the URL of the resource.
   * {{{<object>}}}
   */
  final public Attr<Effect<State, Message>> data(String value) {
    return htmlAttr("data", value);
  }

  /**
   * Indicates the date and time associated with the element.
   * {{{<del>, <ins>, <time>}}}
   */
  final public Attr<Effect<State, Message>> datetime(String value) {
    return htmlAttr("datetime", value);
  }

  /**
   * Indicates that the track should be enabled unless the user's preferences indicate something different.
   * {{{<track>}}}
   */
  final public Attr<Effect<State, Message>> $default(String value) {
    return htmlAttr("default", value);
  }

  /**
   * Indicates that the script should be executed after the page has been parsed.
   * {{{<script>}}}
   */
  final public Attr<Effect<State, Message>> defer(String value) {
    return htmlAttr("defer", value);
  }

  /**
   * Defines the text direction. Allowed values are ltr (Left-To-Right) or rtl (Right-To-Left)
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> dir(String value) {
    return htmlAttr("dir", value);
  }

  /**
   * 
   * {{{<input>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> dirname(String value) {
    return htmlAttr("dirname", value);
  }

  /**
   * Indicates whether the user can interact with the element.
   * {{{<button>, <command>, <fieldset>, <input>, <keygen>, <optgroup>, <option>, <select>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> disabled(String value) {
    return htmlAttr("disabled", value);
  }

  /**
   * Indicates that the hyperlink is to be used for downloading a resource.
   * {{{<a>, <area>}}}
   */
  final public Attr<Effect<State, Message>> download(String value) {
    return htmlAttr("download", value);
  }

  /**
   * Defines whether the element can be dragged.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> draggable(String value) {
    return htmlAttr("draggable", value);
  }

  /**
   * Indicates that the element accept the dropping of content on it.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> dropzone(String value) {
    return htmlAttr("dropzone", value);
  }

  /**
   * Defines the content type of the form date when the method is POST.
   * {{{<form>}}}
   */
  final public Attr<Effect<State, Message>> enctype(String value) {
    return htmlAttr("enctype", value);
  }

  /**
   * Describes elements which belongs to this one.
   * {{{<label>, <output>}}}
   */
  final public Attr<Effect<State, Message>> $for(String value) {
    return htmlAttr("for", value);
  }

  /**
   * Indicates the form that is the owner of the element.
   * {{{<button>, <fieldset>, <input>, <keygen>, <label>, <meter>, <object>, <output>, <progress>, <select>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> form(String value) {
    return htmlAttr("form", value);
  }

  /**
   * Indicates the action of the element, overriding the action defined in the <form>.
   * {{{<input>, <button>}}}
   */
  final public Attr<Effect<State, Message>> formaction(String value) {
    return htmlAttr("formaction", value);
  }

  /**
   * IDs of the <th> elements which applies to this element.
   * {{{<td>, <th>}}}
   */
  final public Attr<Effect<State, Message>> headers(String value) {
    return htmlAttr("headers", value);
  }

  /**
   * Specifies the height of elements listedhere. For all other elements, use the CSS height property. Note: In some instances, such as <div>, this is alegacy attribute, in which case the CSS height property should be used instead.
   * {{{<canvas>, <embed>, <iframe>, <img>, <input>, <object>, <video>}}}
   */
  final public Attr<Effect<State, Message>> height(String value) {
    return htmlAttr("height", value);
  }

  /**
   * Prevents rendering of given element, while keeping child elements, e.g. script elements, active.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> hidden(String value) {
    return htmlAttr("hidden", value);
  }

  /**
   * Indicates the lower bound of the upper range.
   * {{{<meter>}}}
   */
  final public Attr<Effect<State, Message>> high(String value) {
    return htmlAttr("high", value);
  }

  /**
   *  The URL of a linked resource.
   * {{{<a>, <area>, <base>, <link>}}}
   */
  final public Attr<Effect<State, Message>> href(String value) {
    return htmlAttr("href", value);
  }

  /**
   * Specifies the language of the linked resource.
   * {{{<a>, <area>, <link>}}}
   */
  final public Attr<Effect<State, Message>> hreflang(String value) {
    return htmlAttr("hreflang", value);
  }

  /**
   * 
   * {{{<meta>}}}
   */
  final public Attr<Effect<State, Message>> httpEquiv(String value) {
    return htmlAttr("http-equiv", value);
  }

  /**
   * Specifies a picture which represents the command.
   * {{{<command>}}}
   */
  final public Attr<Effect<State, Message>> icon(String value) {
    return htmlAttr("icon", value);
  }

  /**
   * Often used with CSS to style a specific element. The value of this attribute must be unique.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> id(String value) {
    return htmlAttr("id", value);
  }

  /**
   * Security Feature that allows browsers to verify what they fetch.
   * {{{<link>, <script>}}}
   */
  final public Attr<Effect<State, Message>> integrity(String value) {
    return htmlAttr("integrity", value);
  }

  /**
   * Indicates that the image is part of a server-side image map.
   * {{{<img>}}}
   */
  final public Attr<Effect<State, Message>> ismap(String value) {
    return htmlAttr("ismap", value);
  }

  /**
   * Global attribute
   * {{{ }}}
   */
  final public Attr<Effect<State, Message>> itemprop(String value) {
    return htmlAttr("itemprop", value);
  }

  /**
   * Specifies the type of key generated.
   * {{{<keygen>}}}
   */
  final public Attr<Effect<State, Message>> keytype(String value) {
    return htmlAttr("keytype", value);
  }

  /**
   * Specifies the kind of text track.
   * {{{<track>}}}
   */
  final public Attr<Effect<State, Message>> kind(String value) {
    return htmlAttr("kind", value);
  }

  /**
   * Specifies a user-readable title of the text track.
   * {{{<track>}}}
   */
  final public Attr<Effect<State, Message>> label(String value) {
    return htmlAttr("label", value);
  }

  /**
   * Defines the language used in the element.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> lang(String value) {
    return htmlAttr("lang", value);
  }

  /**
   * Defines the script language used in the element.
   * {{{<script>}}}
   */
  final public Attr<Effect<State, Message>> language(String value) {
    return htmlAttr("language", value);
  }

  /**
   * Identifies a list of pre-defined options to suggest to the user.
   * {{{<input>}}}
   */
  final public Attr<Effect<State, Message>> list(String value) {
    return htmlAttr("list", value);
  }

  /**
   * Indicates whether the media should start playing from the start when it's finished.
   * {{{<audio>, <bgsound>, <marquee>, <video>}}}
   */
  final public Attr<Effect<State, Message>> loop(String value) {
    return htmlAttr("loop", value);
  }

  /**
   * Indicates the upper bound of the lower range.
   * {{{<meter>}}}
   */
  final public Attr<Effect<State, Message>> low(String value) {
    return htmlAttr("low", value);
  }

  /**
   * Specifies the URL of the document's cache manifest.
   * {{{<html>}}}
   */
  final public Attr<Effect<State, Message>> manifest(String value) {
    return htmlAttr("manifest", value);
  }

  /**
   * Indicates the maximum value allowed.
   * {{{<input>, <meter>, <progress>}}}
   */
  final public Attr<Effect<State, Message>> max(String value) {
    return htmlAttr("max", value);
  }

  /**
   * Defines the maximum number of characters allowed in the element.
   * {{{<input>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> maxlength(String value) {
    return htmlAttr("maxlength", value);
  }

  /**
   * Defines the minimum number of characters allowed in the element.
   * {{{<input>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> minlength(String value) {
    return htmlAttr("minlength", value);
  }

  /**
   * Specifies a hint of the media for which the linked resource was designed.
   * {{{<a>, <area>, <link>, <source>, <style>}}}
   */
  final public Attr<Effect<State, Message>> media(String value) {
    return htmlAttr("media", value);
  }

  /**
   * Defines which HTTP method to use when submitting the form. Can be GET (default) or POST.
   * {{{<form>}}}
   */
  final public Attr<Effect<State, Message>> method(String value) {
    return htmlAttr("method", value);
  }

  /**
   * Indicates the minimum value allowed.
   * {{{<input>, <meter>}}}
   */
  final public Attr<Effect<State, Message>> min(String value) {
    return htmlAttr("min", value);
  }

  /**
   * Indicates whether multiple values can be entered in an input of the type email or file.
   * {{{<input>, <select>}}}
   */
  final public Attr<Effect<State, Message>> multiple(String value) {
    return htmlAttr("multiple", value);
  }

  /**
   * Indicates whether the audio will be initially silenced on page load.
   * {{{<audio>, <video>}}}
   */
  final public Attr<Effect<State, Message>> muted(String value) {
    return htmlAttr("muted", value);
  }

  /**
   * Name of the element. For example used by the server to identify the fields in form submits.
   * {{{<button>, <form>, <fieldset>, <iframe>, <input>, <keygen>, <object>, <output>, <select>, <textarea>, <map>, <meta>, <param>}}}
   */
  final public Attr<Effect<State, Message>> name(String value) {
    return htmlAttr("name", value);
  }

  /**
   * This attribute indicates that the form shouldn't be validated when submitted.
   * {{{<form>}}}
   */
  final public Attr<Effect<State, Message>> novalidate(String value) {
    return htmlAttr("novalidate", value);
  }

  /**
   * Indicates whether the details will be shown on page load.
   * {{{<details>}}}
   */
  final public Attr<Effect<State, Message>> open(String value) {
    return htmlAttr("open", value);
  }

  /**
   * Indicates the optimal numeric value.
   * {{{<meter>}}}
   */
  final public Attr<Effect<State, Message>> optimum(String value) {
    return htmlAttr("optimum", value);
  }

  /**
   * Defines a regular expression which the element's value will be validated against.
   * {{{<input>}}}
   */
  final public Attr<Effect<State, Message>> pattern(String value) {
    return htmlAttr("pattern", value);
  }

  /**
   * 
   * {{{<a>, <area>}}}
   */
  final public Attr<Effect<State, Message>> ping(String value) {
    return htmlAttr("ping", value);
  }

  /**
   * Provides a hint to the user of what can be entered in the field.
   * {{{<input>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> placeholder(String value) {
    return htmlAttr("placeholder", value);
  }

  /**
   * A URL indicating a poster frame to show until the user plays or seeks.
   * {{{<video>}}}
   */
  final public Attr<Effect<State, Message>> poster(String value) {
    return htmlAttr("poster", value);
  }

  /**
   * Indicates whether the whole resource, parts of it or nothing should be preloaded.
   * {{{<audio>, <video>}}}
   */
  final public Attr<Effect<State, Message>> preload(String value) {
    return htmlAttr("preload", value);
  }

  /**
   * <command>
   * {{{ }}}
   */
  final public Attr<Effect<State, Message>> radiogroup(String value) {
    return htmlAttr("radiogroup", value);
  }

  /**
   * Indicates whether the element can be edited.
   * {{{<input>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> readonly(String value) {
    return htmlAttr("readonly", value);
  }

  /**
   * Specifies the relationship of the target object to the link object.
   * {{{<a>, <area>, <link>}}}
   */
  final public Attr<Effect<State, Message>> rel(String value) {
    return htmlAttr("rel", value);
  }

  /**
   * Indicates whether this element is required to fill out or not.
   * {{{<input>, <select>, <textarea>}}}
   */
  final public Attr<Effect<State, Message>> required(String value) {
    return htmlAttr("required", value);
  }

  /**
   * Indicates whether the list should be displayed in a descending order instead of a ascending.
   * {{{<ol>}}}
   */
  final public Attr<Effect<State, Message>> reversed(String value) {
    return htmlAttr("reversed", value);
  }

  /**
   * Defines the number of rows in a text area.
   * {{{<textarea>}}}
   */
  final public Attr<Effect<State, Message>> rows(String value) {
    return htmlAttr("rows", value);
  }

  /**
   * Defines the number of rows a table cell should span over.
   * {{{<td>, <th>}}}
   */
  final public Attr<Effect<State, Message>> rowspan(String value) {
    return htmlAttr("rowspan", value);
  }

  /**
   * 
   * {{{<iframe>}}}
   */
  final public Attr<Effect<State, Message>> sandbox(String value) {
    return htmlAttr("sandbox", value);
  }

  /**
   * 
   * {{{<th>}}}
   */
  final public Attr<Effect<State, Message>> scope(String value) {
    return htmlAttr("scope", value);
  }

  /**
   * 
   * {{{<style>}}}
   */
  final public Attr<Effect<State, Message>> scoped(String value) {
    return htmlAttr("scoped", value);
  }

  /**
   * 
   * {{{<iframe> }}}
   */
  final public Attr<Effect<State, Message>> seamless(String value) {
    return htmlAttr("seamless", value);
  }

  /**
   * Defines a value which will be selected on page load.
   * {{{<option>}}}
   */
  final public Attr<Effect<State, Message>> selected(String value) {
    return htmlAttr("selected", value);
  }

  /**
   * 
   * {{{<a>, <area>}}}
   */
  final public Attr<Effect<State, Message>> shape(String value) {
    return htmlAttr("shape", value);
  }

  /**
   * Defines the width of the element (in pixels). If the element's type attribute is text or password then it's the number of characters.
   * {{{<input>, <select>}}}
   */
  final public Attr<Effect<State, Message>> size(String value) {
    return htmlAttr("size", value);
  }

  /**
   * 
   * {{{<link>, <img>, <source>}}}
   */
  final public Attr<Effect<State, Message>> sizes(String value) {
    return htmlAttr("sizes", value);
  }

  /**
   * Assigns a slot in a shadow DOM shadow tree to an element.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> slot(String value) {
    return htmlAttr("slot", value);
  }

  /**
   * 
   * {{{<col>, <colgroup>}}}
   */
  final public Attr<Effect<State, Message>> span(String value) {
    return htmlAttr("span", value);
  }

  /**
   * Indicates whether spell checking is allowed for the element.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> spellcheck(String value) {
    return htmlAttr("spellcheck", value);
  }

  /**
   * The URL of the embeddable content.
   * {{{<audio>, <embed>, <iframe>, <img>, <input>, <script>, <source>, <track>, <video>}}}
   */
  final public Attr<Effect<State, Message>> src(String value) {
    return htmlAttr("src", value);
  }

  /**
   * 
   * {{{<iframe>}}}
   */
  final public Attr<Effect<State, Message>> srcdoc(String value) {
    return htmlAttr("srcdoc", value);
  }

  /**
   * 
   * {{{<track>}}}
   */
  final public Attr<Effect<State, Message>> srclang(String value) {
    return htmlAttr("srclang", value);
  }

  /**
   * 
   * {{{<img>}}}
   */
  final public Attr<Effect<State, Message>> srcset(String value) {
    return htmlAttr("srcset", value);
  }

  /**
   * Defines the first number if other than 1.
   * {{{<ol>}}}
   */
  final public Attr<Effect<State, Message>> start(String value) {
    return htmlAttr("start", value);
  }

  /**
   * 
   * {{{<input>}}}
   */
  final public Attr<Effect<State, Message>> step(String value) {
    return htmlAttr("step", value);
  }

  /**
   * Defines CSS styles which will override styles previously set.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> style(String value) {
    return htmlAttr("style", value);
  }

  /**
   * 
   * {{{<table>}}}
   */
  final public Attr<Effect<State, Message>> summary(String value) {
    return htmlAttr("summary", value);
  }

  /**
   * Overrides the browser's default tab order and follows the one specified instead.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> tabindex(String value) {
    return htmlAttr("tabindex", value);
  }

  /**
   * 
   * {{{<a>, <area>, <base>, <form>}}}
   */
  final public Attr<Effect<State, Message>> target(String value) {
    return htmlAttr("target", value);
  }

  /**
   * Text to be displayed in a tooltip when hovering over the element.
   * {{{Global attribute}}}
   */
  final public Attr<Effect<State, Message>> title(String value) {
    return htmlAttr("title", value);
  }

  /**
   * Defines the type of the element.
   * {{{<button>, <input>, <command>, <embed>, <object>, <script>, <source>, <style>, <menu>}}}
   */
  final public Attr<Effect<State, Message>> type(String value) {
    return htmlAttr("type", value);
  }

  /**
   * 
   * {{{<img>,  <input>, <object>}}}
   */
  final public Attr<Effect<State, Message>> usemap(String value) {
    return htmlAttr("usemap", value);
  }

  /**
   * Defines a default value which will be displayed in the element on page load.
   * {{{<button>, <option>, <input>, <li>, <meter>, <progress>, <param>}}}
   */
  final public Attr<Effect<State, Message>> value(String value) {
    return htmlAttr("value", value);
  }

  /**
   * For the elements listed here, this establishes the element's width. Note: For all other instances, such as <div>, this is a legacy attribute, in which case the CSS width property should be used instead.
   * {{{<canvas>, <embed>, <iframe>, <img>, <input>, <object>, <video>}}}
   */
  final public Attr<Effect<State, Message>> width(String value) {
    return htmlAttr("width", value);
  }

  /**
   * Indicates whether the text should be wrapped.
   * {{{<textarea>}}}
   */
  final public Attr<Effect<State, Message>> wrap(String value) {
    return htmlAttr("wrap", value);
  }

}
