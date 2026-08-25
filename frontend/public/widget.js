(function () {
  'use strict';

  var config = (window.CallsagentsConfig && window.CallsagentsConfig.baseUrl) || window.location.origin;
  var widgetUrl = config.replace(/\/+$/, '') + '/widget';

  var STYLE_ID = 'callsagents-widget-style';
  if (!document.getElementById(STYLE_ID)) {
    var style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent =
      '#callsagents-bubble{position:fixed;bottom:24px;right:24px;z-index:2147483647;width:60px;height:60px;border-radius:50%;background:#00a86b;border:none;cursor:pointer;box-shadow:0 4px 24px rgba(0,0,0,.3);display:flex;align-items:center;justify-content:center;transition:transform .2s,box-shadow .2s}#callsagents-bubble:hover{transform:scale(1.08);box-shadow:0 6px 32px rgba(0,0,0,.4)}#callsagents-bubble svg{width:28px;height:28px;color:#fff}#callsagents-bubble.ca-pulse{animation:ca-pulse 2s ease-in-out infinite}@keyframes ca-pulse{0%,100%{box-shadow:0 4px 24px rgba(0,0,0,.3)}50%{box-shadow:0 4px 24px rgba(0,168,107,.6),0 0 0 8px rgba(0,168,107,.15)}}#callsagents-panel{position:fixed;bottom:96px;right:24px;z-index:2147483647;width:350px;height:500px;border:none;border-radius:16px;box-shadow:0 8px 48px rgba(0,0,0,.35);background:#0f172a;display:none;overflow:hidden}#callsagents-panel.ca-open{display:block;animation:ca-slide-in .25s ease-out}@keyframes ca-slide-in{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:translateY(0)}}';
    document.head.appendChild(style);
  }

  var ICON_PATH = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>';
  var ICON_CLOSE = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>';

  var bubble = document.createElement('button');
  bubble.id = 'callsagents-bubble';
  bubble.className = 'ca-pulse';
  bubble.setAttribute('aria-label', 'Abrir chat');
  bubble.innerHTML = ICON_PATH;

  var panel = document.createElement('div');
  panel.id = 'callsagents-panel';

  document.body.appendChild(bubble);
  document.body.appendChild(panel);

  var open = false;

  function toggle() {
    open = !open;
    if (open) {
      bubble.innerHTML = ICON_CLOSE;
      bubble.classList.remove('ca-pulse');
      panel.classList.add('ca-open');
      if (!panel.querySelector('iframe')) {
        var iframe = document.createElement('iframe');
        iframe.src = widgetUrl;
        iframe.style.width = '100%';
        iframe.style.height = '100%';
        iframe.style.border = 'none';
        iframe.title = 'CallsAgents Chat';
        panel.appendChild(iframe);
      }
    } else {
      bubble.innerHTML = ICON_PATH;
      panel.classList.remove('ca-open');
    }
  }

  bubble.addEventListener('click', toggle);
})();
