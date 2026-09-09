document.addEventListener('DOMContentLoaded', () => {
  const container = document.getElementById('proyecto-items-container');
  const addBtn = document.getElementById('btn-add-item');
  const template = document.getElementById('proyecto-item-template');

  if (!container || !addBtn || !template) return;

  addBtn.addEventListener('click', () => {
    const clone = template.content.cloneNode(true);
    const removeBtn = clone.querySelector('.btn-remove-item');
    if (removeBtn) {
      removeBtn.addEventListener('click', (e) => {
        e.target.closest('.proyecto-item-row-edit').remove();
      });
    }
    container.appendChild(clone);
  });

  // Attach click listener for pre-existing remove buttons (e.g. edit view)
  container.querySelectorAll('.btn-remove-item').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.target.closest('.proyecto-item-row-edit').remove();
    });
  });
});
