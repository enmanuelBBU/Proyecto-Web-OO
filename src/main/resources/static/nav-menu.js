/* Mejora progresiva de la topbar: sin JS los <details> siguen funcionando. */
(function () {
  'use strict';

  function init() {
    var topbar = document.querySelector('.topbar');
    if (!topbar) return;
    var menus = Array.prototype.slice.call(topbar.querySelectorAll('details.menu'));
    var toggle = topbar.querySelector('.topbar-toggle');
    var desktop = window.matchMedia('(min-width: 900px)');

    function closeMenus(except) {
      menus.forEach(function (d) { if (d !== except) d.open = false; });
    }

    function setPanel(open) {
      topbar.classList.toggle('is-open', open);
      if (toggle) {
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        toggle.setAttribute('aria-label', open ? 'Cerrar menú' : 'Abrir menú');
      }
      // En móvil los grupos se muestran expandidos dentro del panel.
      menus.forEach(function (d) { d.open = open && !desktop.matches; });
    }

    menus.forEach(function (d) {
      d.addEventListener('toggle', function () {
        if (d.open && desktop.matches) closeMenus(d);
      });
      d.addEventListener('click', function (e) {
        if (e.target.closest && e.target.closest('.menu-panel a')) {
          d.open = false;
          if (topbar.classList.contains('is-open')) setPanel(false);
        }
      });
    });

    if (toggle) {
      toggle.addEventListener('click', function () {
        setPanel(!topbar.classList.contains('is-open'));
      });
    }

    document.addEventListener('click', function (e) {
      if (!topbar.contains(e.target)) {
        if (topbar.classList.contains('is-open')) setPanel(false);
        closeMenus(null);
      }
    });

    document.addEventListener('keydown', function (e) {
      if (e.key !== 'Escape' && e.key !== 'Esc') return;
      if (topbar.classList.contains('is-open')) {
        setPanel(false);
        if (toggle) toggle.focus();
        return;
      }
      var abierto = menus.filter(function (d) { return d.open; })[0];
      if (abierto) {
        abierto.open = false;
        var summary = abierto.querySelector('summary');
        if (summary) summary.focus();
      }
    });

    function onBreakpoint() {
      setPanel(false);
      closeMenus(null);
    }
    if (desktop.addEventListener) desktop.addEventListener('change', onBreakpoint);
    else if (desktop.addListener) desktop.addListener(onBreakpoint);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
