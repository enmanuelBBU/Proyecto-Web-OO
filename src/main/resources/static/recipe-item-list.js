document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('.recipe-item-list').forEach(function (list) {
    fetch('/api/items')
      .then(function (res) { return res.json(); })
      .then(function (itemsList) {
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
            ev.dataTransfer.setData('text/plain', item.id);
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
  if (e.target.matches('.recipe-grid input')) e.preventDefault();
});

document.addEventListener('dragenter', function (e) {
  if (e.target.matches('.recipe-grid input')) e.target.classList.add('drag-over');
});

document.addEventListener('dragleave', function (e) {
  if (e.target.matches('.recipe-grid input')) e.target.classList.remove('drag-over');
});

document.addEventListener('drop', function (e) {
  if (!e.target.matches('.recipe-grid input')) return;
  e.preventDefault();
  e.target.classList.remove('drag-over');
  var id = e.dataTransfer.getData('text/plain');
  if (id) e.target.value = id;
});
