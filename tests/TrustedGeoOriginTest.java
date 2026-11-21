package io.github.jok86.pokehabitwrapper;

public class TrustedGeoOriginTest {
  static int count=0;
  static void check(String name,boolean expected,String origin,String url){
    boolean actual=TrustedGeoOrigin.accepts(origin,url);
    if(actual!=expected)throw new AssertionError(name+" expected="+expected+" actual="+actual);
    System.out.println("PASS " + name);count++;
  }
  public static void main(String[] args){
    String app="https://j-ok-86.github.io/PokeHabit/";
    check("canonical origin",true,"https://j-ok-86.github.io",app);
    check("trailing slash",true,"https://j-ok-86.github.io/",app);
    check("explicit HTTPS 443",true,"https://j-ok-86.github.io:443/",app);
    check("app index page",true,"https://j-ok-86.github.io",app+"index.html");
    check("app subpage",true,"https://j-ok-86.github.io/",app+"moves.html");
    check("app url without slash",true,"https://j-ok-86.github.io","https://j-ok-86.github.io/PokeHabit");
    check("wrong app path",false,"https://j-ok-86.github.io","https://j-ok-86.github.io/other/");
    check("path prefix attack",false,"https://j-ok-86.github.io","https://j-ok-86.github.io/PokeHabitEvil/");
    check("path traversal",false,"https://j-ok-86.github.io","https://j-ok-86.github.io/PokeHabit/../other/");
    check("untrusted iframe",false,"https://evil.example",app);
    check("subdomain",false,"https://x.j-ok-86.github.io",app);
    check("http origin",false,"http://j-ok-86.github.io",app);
    check("http main frame",false,"https://j-ok-86.github.io","http://j-ok-86.github.io/PokeHabit/");
    check("bad origin port",false,"https://j-ok-86.github.io:8443/",app);
    check("bad page port",false,"https://j-ok-86.github.io","https://j-ok-86.github.io:8443/PokeHabit/");
    check("origin with URL path",false,"https://j-ok-86.github.io/PokeHabit/",app);
    check("origin with query",false,"https://j-ok-86.github.io/?id=1",app);
    check("origin with fragment",false,"https://j-ok-86.github.io/#x",app);
    check("userinfo",false,"https://someone@j-ok-86.github.io",app);
    check("host spoofing",false,"https://j-ok-86.github.io.evil.example",app);
    check("null origin",false,null,app);
    check("null page",false,"https://j-ok-86.github.io",null);
    System.out.println("ALL PASS: "+count);
  }
}
