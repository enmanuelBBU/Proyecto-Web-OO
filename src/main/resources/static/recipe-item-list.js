function fillSlotVisual(slot, item) {
  var icon = slot.querySelector('.recipe-slot-icon');
  var name = slot.querySelector('.recipe-slot-name');
  var input = slot.querySelector('input[name="slot"]');
  var remove = slot.querySelector('.recipe-slot-remove');
  input.value = item.id;
  name.textContent = item.nombre;
  icon.src = item.fullId
    ? 'https://blocksitems.com/api/v1/items/' + item.fullId + '/icon?size=32'
    : '/img/item-generico.svg';
  icon.style.display = 'block';
  icon.onerror = function () { this.src = '/img/item-generico.svg'; };
  if (remove) remove.style.display = 'flex';
}

function clearSlotVisual(slot) {
  var icon = slot.querySelector('.recipe-slot-icon');
  var name = slot.querySelector('.recipe-slot-name');
  var input = slot.querySelector('input[name="slot"]');
  var remove = slot.querySelector('.recipe-slot-remove');
  input.value = '';
  name.textContent = '';
  icon.removeAttribute('src');
  icon.style.display = 'none';
  if (remove) remove.style.display = 'none';
}

document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('.recipe-item-list').forEach(function (list) {
    fetch('/api/items')
      .then(function (res) { return res.json(); })
      .then(function (itemsList) {
        var byId = {};
        itemsList.forEach(function (item) { byId[item.id] = item; });

        // resolve any already-filled slots (edit form) to their icon + name
        document.querySelectorAll('.recipe-slot').forEach(function (slot) {
          var input = slot.querySelector('input[name="slot"]');
          if (input && input.value && byId[input.value]) {
            fillSlotVisual(slot, byId[input.value]);
          }
        });

        list.innerHTML = '';
        if (!itemsList.length) {
          list.textContent = 'No hay items en el catálogo todavía.';
          return;
        }
        itemsList.forEach(function (item) {
          var card = document.createElement('div');
          card.className = 'recipe-item-card';
          card.draggable = true;
          card.title = item.nombre;

          var img = document.createElement('img');
          img.src = item.fullId
            ? 'https://blocksitems.com/api/v1/items/' + item.fullId + '/icon?size=32'
            : '/img/item-generico.svg';
          img.alt = '';
          img.onerror = function () { this.src = '/img/item-generico.svg'; };

          var span = document.createElement('span');
          span.textContent = item.nombre;

          card.appendChild(img);
          card.appendChild(span);
          card.addEventListener('dragstart', function (ev) {
            ev.dataTransfer.setData('text/plain', JSON.stringify(item));
          });
          list.appendChild(card);
        });
      })
      .catch(function () {
        list.textContent = 'No se pudieron cargar los items.';
      });
  });
});

document.addEventListener('dragover', function (e) {
  if (e.target.closest('.recipe-slot')) e.preventDefault();
});

document.addEventListener('dragenter', function (e) {
  var slot = e.target.closest('.recipe-slot');
  if (slot) slot.classList.add('drag-over');
});

document.addEventListener('dragleave', function (e) {
  var slot = e.target.closest('.recipe-slot');
  if (slot) slot.classList.remove('drag-over');
});

document.addEventListener('drop', function (e) {
  var slot = e.target.closest('.recipe-slot');
  if (!slot) return;
  e.preventDefault();
  slot.classList.remove('drag-over');
  var raw = e.dataTransfer.getData('text/plain');
  if (!raw) return;
  try {
    fillSlotVisual(slot, JSON.parse(raw));
  } catch (err) {
    // ignore drops that don't carry a valid item payload
  }
});

document.addEventListener('click', function (e) {
  var remove = e.target.closest('.recipe-slot-remove');
  if (!remove) return;
  var slot = remove.closest('.recipe-slot');
  if (slot) clearSlotVisual(slot);
});
