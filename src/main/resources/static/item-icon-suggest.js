document.addEventListener('click', function (e) {
  var btn = e.target.closest('.sugerir-icono-btn');
  if (!btn) return;

  var nombreInput = document.getElementById(btn.dataset.nombre);
  var fullIdInput = document.getElementById(btn.dataset.fullid);
  var target = document.getElementById(btn.dataset.target);
  var nombre = nombreInput ? nombreInput.value.trim() : '';

  if (!nombre) {
    target.textContent = 'Escribe un nombre primero.';
    return;
  }

  target.textContent = 'Buscando...';

  fetch('/api/items/sugerencias-icono?nombre=' + encodeURIComponent(nombre))
    .then(function (res) { return res.json(); })
    .then(function (candidates) {
      target.textContent = '';
      if (!candidates.length) {
        target.textContent = 'No se encontraron sugerencias, ingresa el id manualmente.';
        return;
      }
      candidates.forEach(function (c) {
        var card = document.createElement('button');
        card.type = 'button';
        card.className = 'icon-candidate';
        var img = document.createElement('img');
        img.src = c.iconUrl;
        img.alt = '';
        img.onerror = function () { this.style.display = 'none'; };
        var span = document.createElement('span');
        span.textContent = c.displayName;
        card.appendChild(img);
        card.appendChild(span);
        card.addEventListener('click', function () {
          fullIdInput.value = c.fullId;
        });
        target.appendChild(card);
      });
    })
    .catch(function () {
      target.textContent = 'No se pudo buscar sugerencias.';
    });
});
