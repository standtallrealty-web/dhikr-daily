var C='dhikr-v21',F='dhikr-fonts';
self.addEventListener('install',function(e){
  self.skipWaiting();
  e.waitUntil(caches.open(C).then(function(c){
    return Promise.all(['/','/manifest.webmanifest','/icon-192.png','/icon-512.png'].map(function(u){return c.add(u).catch(function(){})}));
  }));
});
self.addEventListener('activate',function(e){
  e.waitUntil(caches.keys().then(function(k){return Promise.all(k.filter(function(x){return x!==C&&x!==F}).map(function(x){return caches.delete(x)}))}).then(function(){return self.clients.claim()}));
});
self.addEventListener('fetch',function(e){
  var r=e.request,u=new URL(r.url);
  if(r.method==='GET'&&(u.hostname==='fonts.googleapis.com'||u.hostname==='fonts.gstatic.com')){
    e.respondWith(caches.open(F).then(function(c){return c.match(r).then(function(m){
      return m||fetch(r).then(function(res){if(res&&(res.ok||res.type==='opaque'))c.put(r,res.clone());return res}).catch(function(){return m})})}));
    return;
  }
  // Only handle our own pages/files. Never touch audio or API requests to other sites.
  if(r.method!=='GET'||new URL(r.url).origin!==self.location.origin||r.headers.has('range'))return;
  e.respondWith(fetch(r).then(function(res){
    if(res&&res.ok&&!res.redirected){var cp=res.clone();caches.open(C).then(function(c){c.put(r,cp)}).catch(function(){})}
    return res;
  }).catch(function(){
    return caches.match(r).then(function(m){return m||caches.match('/')});
  }));
});
