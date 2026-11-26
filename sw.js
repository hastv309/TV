/* Offline cache for the TV home page and screensaver.
   Pages and data: network first (short timeout), else the last saved copy.
   Images and fonts: saved copy first, else network. */
var V="tv-v1",PAGES="pages-"+V,MEDIA="media-"+V;
self.addEventListener("install",function(e){
  e.waitUntil(caches.open(PAGES).then(function(c){return c.addAll(["home.html","index.html","data.json","news.json"])}).catch(function(){}));
  self.skipWaiting()});
self.addEventListener("activate",function(e){
  e.waitUntil(caches.keys().then(function(ks){return Promise.all(ks.filter(function(k){return k!==PAGES&&k!==MEDIA}).map(function(k){return caches.delete(k)}))}).then(function(){return self.clients.claim()}))});
function bare(u){var x=new URL(u);x.search="";x.hash="";return x.href}
function netFirst(req,key){
  return new Promise(function(res){
    var done=false,t=setTimeout(function(){caches.match(key).then(function(r){if(r&&!done){done=true;res(r)}})},6000);
    fetch(req).then(function(r){
      if(r&&r.ok){var cp=r.clone();caches.open(PAGES).then(function(c){c.put(key,cp)})}
      if(!done){done=true;clearTimeout(t);res(r)}
    }).catch(function(){caches.match(key).then(function(r){if(!done){done=true;clearTimeout(t);res(r||Response.error())}})})})}
function trim(c,max){c.keys().then(function(ks){if(ks.length>max)ks.slice(0,ks.length-max).forEach(function(k){c.delete(k)})})}
function cacheFirst(req){
  return caches.open(MEDIA).then(function(c){return c.match(req.url).then(function(hit){
    if(hit)return hit;
    return fetch(req).then(function(r){if(r&&(r.ok||r.type==="opaque")){c.put(req.url,r.clone());trim(c,120)}return r})})})}
self.addEventListener("fetch",function(e){
  var req=e.request;if(req.method!=="GET")return;
  var u=new URL(req.url),own=u.origin===self.location.origin;
  if(own&&(req.mode==="navigate"||/\.(html|json)$/.test(u.pathname)||/\/TV\/?$/.test(u.pathname))){
    var key=bare(req.url);if(/\/TV\/?$/.test(u.pathname))key=new URL("index.html",self.registration.scope).href;
    e.respondWith(netFirst(req,key));return}
  if(u.hostname==="api.open-meteo.com"){e.respondWith(netFirst(req,req.url.replace(/[?&]t=\d+/,"")));return}
  if(/\.(jpg|jpeg|png|webp|woff2?)$/.test(u.pathname)||u.hostname==="images.unsplash.com"||u.hostname==="fonts.gstatic.com"||u.hostname==="fonts.googleapis.com"||u.hostname==="upload.wikimedia.org"||/mzstatic\.com$/.test(u.hostname)){
    e.respondWith(cacheFirst(req));return}
});
