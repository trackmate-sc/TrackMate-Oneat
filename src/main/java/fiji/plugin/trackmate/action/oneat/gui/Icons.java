/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2022 - 2023 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate.action.oneat.gui;

import javax.swing.ImageIcon;
import java.awt.Image;

public class Icons {
	

		public static final ImageIcon ONEAT_ICON = createScaledIcon("images/mtrack.png", 150, 150);
		public static final ImageIcon ONEAT_BIG_ICON = createScaledIcon("images/mtrack.png", 300, 300);
		public static final ImageIcon KAPOORLABS_ICON =  createScaledIcon("images/kapoorlogo.png", 150, 150);


		public static ImageIcon createScaledIcon(String path, int width, int height) {
        // Load the original ImageIcon
        ImageIcon originalIcon = new ImageIcon(Icons.class.getResource(path));
        
        // Get the Image from the ImageIcon
        Image originalImage = originalIcon.getImage();
        
        // Scale the Image to the desired size
        Image scaledImage = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        
        // Create a new ImageIcon from the scaled Image
        return new ImageIcon(scaledImage);
    }
}

