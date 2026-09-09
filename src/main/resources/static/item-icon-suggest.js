(function () {
  'use strict';

  function iconUrl(fullId, size) {
    var id = (fullId || '').replace(/^minecraft:/, '');
    return 'https://blocksitems.com/api/v1/items/' + encodeURIComponent(id) + '/icon?size=' + size;
  }

  // ---------- preview por fullId ----------
  function actualizarPreview(fullIdInput) {
    var previewId = fullIdInput.getAttribute('data-preview');
    if (!previewId) return;
    var box = document.getElementById(previewId);
    if (!box) return;

    var img = box.querySelector('.icon-preview-img');
    var txt = box.querySelector('.icon-preview-text');
    var val = (fullIdInput.value || '').trim().replace(/^minecraft:/, '');

    if (!val) {
      img.hidden = true;
      img.removeAttribute('src');
      txt.textContent = 'Sin icono';
      return;
    }

    img.hidden = false;
    img.onload = function () { txt.textContent = val; };
    img.onerror = function () { img.hidden = true; txt.textContent = 'Icono no disponible'; };
    img.src = iconUrl(val, 64);
  }

  function initPreview(fullIdInput) {
    fullIdInput.addEventListener('input', function () { actualizarPreview(fullIdInput); });
    fullIdInput.addEventListener('change', function () { actualizarPreview(fullIdInput); });
    actualizarPreview(fullIdInput);
  }

  // ---------- busqueda de iconos por palabra clave ----------
  function buscarIconos(btn) {
    var keywordInput = document.getElementById(btn.getAttribute('data-buscar'));
    var fullIdInput = document.getElementById(btn.getAttribute('data-fullid'));
    var target = document.getElementById(btn.getAttribute('data-target'));
    var keyword = keywordInput ? keywordInput.value.trim() : '';

    if (!target) return;
    if (!keyword) {
      target.textContent = 'Escribe una palabra clave o textura primero.';
      return;
    }

    target.textContent = 'Buscando...';

    fetch('/api/items/sugerencias-icono?nombre=' + encodeURIComponent(keyword))
      .then(function (res) { return res.json(); })
      .then(function (candidates) {
        target.textContent = '';
        if (!candidates.length) {
          target.textContent = 'No se encontraron sugerencias para "' + keyword + '". Escribe el Full ID manualmente.';
          return;
        }
        candidates.forEach(function (c) {
          var card = document.createElement('button');
          card.type = 'button';
          card.className = 'icon-candidate';
          card.title = c.displayName;

          var img = document.createElement('img');
          img.src = c.iconUrl;
          img.alt = '';
          img.onerror = function () { this.style.display = 'none'; };

          var span = document.createElement('span');
          span.textContent = c.displayName;

          card.appendChild(img);
          card.appendChild(span);
          card.addEventListener('click', function () {
            target.querySelectorAll('.icon-candidate.selected').forEach(function (el) {
              el.classList.remove('selected');
            });
            card.classList.add('selected');
            fullIdInput.value = c.fullId;
            actualizarPreview(fullIdInput);
          });
          target.appendChild(card);
        });
      })
      .catch(function () {
        target.textContent = 'No se pudo buscar sugerencias.';
      });
  }

  // ---------- wiring ----------
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('input[name="fullId"]').forEach(function (input) {
      initPreview(input);
    });
    document.querySelectorAll('.icon-buscar-btn').forEach(function (btn) {
      btn.addEventListener('click', function () { buscarIconos(btn); });
    });
  });
})();