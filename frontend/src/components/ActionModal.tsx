export function ActionModal() {
  return (
    <div className="modal-bg" id="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div className="modal">
        <h3 id="modal-title" data-i18n="modal.action">Action</h3>
        <div className="modal-field">
          <label htmlFor="modal-player" data-i18n="modal.player">Player</label>
          <input id="modal-player" name="modalPlayer" className="notranslate" type="text" readOnly translate="no" />
        </div>
        <div className="modal-field">
          <label htmlFor="modal-reason" data-i18n="modal.reason">Reason</label>
          <input id="modal-reason" name="modalReason" type="text" placeholder="Enter reason&#8230;" data-i18n-placeholder="modal.reasonPlaceholder" />
        </div>
        <div className="modal-actions">
          <button className="mbtn cancel" id="modal-cancel" data-i18n="modal.cancel">Cancel</button>
          <button className="mbtn confirm" id="modal-confirm" data-i18n="modal.confirm">Confirm</button>
        </div>
      </div>
    </div>
  );
}
