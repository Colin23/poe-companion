"use strict";

// Keep native <details> toggling while dismissing the menu when focus or a click leaves it.
const mobileMenu = document.querySelector("details.mobile-navigation");

if (mobileMenu) {
    const closeMenu = () => {
        mobileMenu.open = false;
    };

    document.addEventListener("click", (event) => {
        if (mobileMenu.open && !mobileMenu.contains(event.target)) {
            closeMenu();
        }
    });

    document.addEventListener("focusin", (event) => {
        if (mobileMenu.open && !mobileMenu.contains(event.target)) {
            closeMenu();
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && mobileMenu.open) {
            closeMenu();
            mobileMenu.querySelector("summary").focus();
        }
    });
}
